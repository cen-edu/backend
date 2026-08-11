from __future__ import annotations

import copy
import io
import json
import mimetypes
import unicodedata
import zipfile
from pathlib import Path
from typing import Any

from PIL import Image


IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp"}


def _first_dict(value: Any) -> dict:
    while isinstance(value, list):
        value = value[0] if value else {}
    return value if isinstance(value, dict) else {}


def _normalized_name(value: str) -> str:
    return unicodedata.normalize("NFC", Path(str(value)).name)


def _regions_110_111(raw: dict) -> list[dict]:
    ocr = _first_dict(raw.get("OCR_info"))
    alt = str(ocr.get("figure_text") or "").strip()
    regions = []
    for item in ocr.get("question_bbox") or []:
        if not isinstance(item, dict) or str(item.get("type") or "").lower() not in {"image", "table"}:
            continue
        bbox = item.get("bbox")
        if isinstance(bbox, list) and len(bbox) == 4:
            regions.append({"bbox": bbox, "altText": str(item.get("text") or alt).strip(),
                            "assetRole": "TABLE" if str(item.get("type")).lower() == "table" else "FIGURE"})
    return regions


def _regions_30(raw: dict) -> list[dict]:
    regions = []
    for block in raw.get("learning_data_info") or []:
        if not isinstance(block, dict) or "문항(이미지)" not in str(block.get("class_name") or ""):
            continue
        for item in block.get("class_info_list") or []:
            if not isinstance(item, dict):
                continue
            for bbox in item.get("Type_value") or []:
                if isinstance(bbox, list) and len(bbox) == 4:
                    regions.append({"bbox": bbox, "altText": str(item.get("text_description") or "").strip(),
                                    "assetRole": "FIGURE"})
    return regions


def _source_name(raw: dict, dataset: str) -> str:
    if dataset == "30":
        return f"{(raw.get('source_data_info') or {}).get('source_data_name', '')}.png"
    return str(raw.get("question_filename") or "")


def package_record_assets(record: dict, raw: dict, dataset: str, source_image: Path | bytes | None,
                          output_root: Path) -> tuple[dict, list[dict]]:
    output = copy.deepcopy(record)
    regions = _regions_30(raw) if dataset == "30" else _regions_110_111(raw)
    if not regions:
        output["assets"] = []
        output["presentation"] = "TEXT_ONLY"
        return output, []
    if source_image is None:
        output["assets"] = []
        output["presentation"] = "TEXT_ONLY"
        return output, [{"code": "SOURCE_IMAGE_MISSING", "message": "bbox에 대응하는 원본 이미지를 찾을 수 없습니다.", "path": _source_name(raw, dataset)}]
    try:
        image = Image.open(io.BytesIO(source_image) if isinstance(source_image, bytes) else source_image)
        image.load()
    except (OSError, ValueError) as error:
        output["assets"] = []
        output["presentation"] = "TEXT_ONLY"
        return output, [{"code": "SOURCE_IMAGE_INVALID", "message": str(error), "path": _source_name(raw, dataset)}]
    source_name = _normalized_name(_source_name(raw, dataset))
    stem = Path(source_name).stem
    assets, figure_blocks = [], []
    for index, region in enumerate(regions, 1):
        left, top, right, bottom = (float(value) for value in region["bbox"])
        crop_box = (max(0, round(left)), max(0, round(top)), min(image.width, round(right)), min(image.height, round(bottom)))
        if crop_box[2] <= crop_box[0] or crop_box[3] <= crop_box[1]:
            continue
        suffix = f"TB{index}" if region["assetRole"] == "TABLE" else f"F{index}"
        # ERD joins content_blocks.assetRef to question_asset.asset_key.
        asset_id = suffix
        file_name = f"{stem}_{suffix}.png"
        storage_key = f"questions/{dataset}/{file_name}"
        target = Path(output_root) / storage_key
        target.parent.mkdir(parents=True, exist_ok=True)
        cropped = image.crop(crop_box)
        cropped.save(target, format="PNG")
        assets.append({
            "altText": region["altText"], "assetRole": region["assetRole"], "assetId": asset_id,
            "assetKey": asset_id, "blockId": f"Q-IMG-{index}",
            "contentType": "image/png", "heightPx": cropped.height, "leakChecked": True,
            "ownerRef": None, "ownerType": "QUESTION", "seq": index,
            "sourceBbox": [left, top, right, bottom], "sourceDataset": dataset,
            "sourceImage": source_name, "storageKey": storage_key, "widthPx": cropped.width,
        })
        figure_blocks.append({"assetRef": asset_id, "blockId": f"Q-IMG-{index}",
                              "blockKind": "FIGURE", "text": region["altText"]})
    existing = [block for block in output.get("contentBlocks") or [] if block.get("blockKind") != "FIGURE_TEXT"]
    output["contentBlocks"] = [*existing, *figure_blocks]
    output["assets"] = assets
    output["presentation"] = "WITH_FIGURE" if assets else "TEXT_ONLY"
    return output, []


class SourceMediaIndex:
    def __init__(self, root: Path):
        self.files: dict[str, Path] = {}
        self.archives: dict[str, tuple[Path, str]] = {}
        root = Path(root)
        for path in root.rglob("*"):
            if path.is_file() and path.suffix.lower() in IMAGE_SUFFIXES:
                self.files.setdefault(_normalized_name(path.name), path)
        for archive_path in root.rglob("*.zip"):
            try:
                with zipfile.ZipFile(archive_path) as archive:
                    for member in archive.namelist():
                        if Path(member).suffix.lower() in IMAGE_SUFFIXES:
                            self.archives.setdefault(_normalized_name(member), (archive_path, member))
            except (OSError, zipfile.BadZipFile):
                continue

    def get(self, name: str) -> Path | bytes | None:
        key = _normalized_name(name)
        if key in self.files:
            return self.files[key]
        location = self.archives.get(key)
        if not location:
            return None
        with zipfile.ZipFile(location[0]) as archive:
            return archive.read(location[1])

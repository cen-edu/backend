package com.cenedu.backend.domain.problem.authoring.semantic.materialization;

import com.cenedu.backend.domain.problem.authoring.diagram.DataTableDiagramSpecV1;
import com.cenedu.backend.domain.problem.authoring.diagram.TableCellSpec;
import com.cenedu.backend.domain.problem.authoring.semantic.evaluation.SemanticResolvedValue;

import java.util.*;

/** 문제은행의 TABLE 블록과 SVG가 같은 resolved 값을 사용하도록 안전한 표 markup을 만든다. */
public final class SemanticTableMarkupFactory {
    private final SemanticVisualDescriptionFactory descriptions = new SemanticVisualDescriptionFactory();

    public String create(DataTableDiagramSpecV1 table, Map<String, SemanticResolvedValue> values) {
        StringBuilder html = new StringBuilder("<table><tbody><tr><th></th>");
        for (String header : table.columnHeaderTemplates()) {
            html.append("<th>").append(escape(descriptions.renderLabel(header, values))).append("</th>");
        }
        html.append("</tr>");
        for (int row = 0; row < table.rowHeaderTemplates().size(); row++) {
            html.append("<tr><th>")
                    .append(escape(descriptions.renderLabel(table.rowHeaderTemplates().get(row), values)))
                    .append("</th>");
            for (int column = 0; column < table.columnHeaderTemplates().size(); column++) {
                TableCellSpec cell = findCell(table, row, column);
                String value = cell == null ? "" : cell.valueKey() == null
                        ? descriptions.renderLabel(cell.textTemplate(), values)
                        : values.get(cell.valueKey()).canonicalValue();
                html.append("<td>").append(escape(value)).append("</td>");
            }
            html.append("</tr>");
        }
        return html.append("</tbody></table>").toString();
    }

    private TableCellSpec findCell(DataTableDiagramSpecV1 table, int row, int column) {
        for (TableCellSpec cell : table.cells()) {
            if (cell.row() == row && cell.column() == column) return cell;
        }
        return null;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
    }
}

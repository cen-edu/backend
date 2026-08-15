-- 해설의 수식 구분자를 보정한다.
-- HTML 표와 보기 기호는 유지하며, answer_raw와 다른 문제 필드는 수정하지 않는다.

UPDATE problem_question
SET explanation = $text$$5.2$의 절댓값은 $|5.2|=5.2$이므로 $a=5.2$이다. 절댓값이 $\tfrac{1}{5}$인 수는 $\tfrac{1}{5}$, $-\tfrac{1}{5}$이고 이 중 양수는 $\tfrac{1}{5}$이므로 $b=\tfrac{1}{5}$이다. 따라서 $a+b=5.2+\tfrac{1}{5}=\tfrac{27}{5}$이다.$text$
WHERE id = 1936;

UPDATE problem_question
SET explanation = $text$$30 \mathrm{~cm}$ 막대의 그림자는 $48 \mathrm{~cm}$, $25 \mathrm{~cm}$ 막대의 그림자는 $40 \mathrm{~cm}$ 이므로 $y=\frac{8}{5}x$가 성립한다. $y=264$일 때, $x=264 \times \frac{5}{8}=165$. 따라서 실제 나무의 높이는 $165\mathrm{~cm}$이다.$text$
WHERE id = 4782;

UPDATE problem_question
SET explanation = $text$기차의 길이를 $x\mathrm{~m}$라고 하면 기차의 속력은 일정하므로 $\frac{700+x}{30}=\frac{1200+x}{50}$이다. 양변에 150을 곱하면 $3500+5x=3600+3x$이므로 $x=50$이다. 따라서 기차의 길이는 $50\mathrm{~m}$이다.$text$
WHERE id = 5449;

UPDATE problem_question
SET explanation = $text$$\frac{x+2}{6}-\frac{3x-2}{4}=a-1$의 양변에 12를 곱하면 \[ \begin{array}{l} 2(x+2)-3(3x-2)=12(a-1) \\ 2x+4-9x+6=12a-12 \\ -7x=12a-22 \\ \therefore x=\frac{-12a+22}{7} \end{array} \] $5x-7=3x-5a$의 해는 $x=\frac{-5a+7}{2}$이다. 두 방정식의 해의 비가 $1:2$이므로 \[ \begin{array}{l} \frac{-12a+22}{7}:\frac{-5a+7}{2}=1:2 \\ \frac{-24a+44}{7}=\frac{-5a+7}{2} \\ -48a+88=-35a+49,\ -13a=-39 \\ \therefore a=3 \end{array} \]$text$
WHERE id = 5452;

UPDATE problem_question
SET explanation = '해설이 없습니다.'
WHERE id IN (3947, 4333)
  AND (explanation IS NULL OR btrim(explanation) = '');

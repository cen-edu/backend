package com.cenedu.backend.ai.problem.adapter;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*; import java.time.Duration; import java.util.*;
import com.cenedu.backend.ai.agent.ChatMessage; import com.cenedu.backend.ai.client.*; import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.*;

@EnabledIfEnvironmentVariable(named="OPENAI_API_KEY", matches=".+")
class ProblemAuthoringModelComparisonLiveTest {
  private static final List<String> CASES=List.of("MULTIPLE_CHOICE:정수의 덧셈 문제를 하나 생성하라.","SHORT_INPUT:일차방정식 단답형 문제를 하나 생성하라.","STEP_FILL:비례식 풀이 단계 문제를 하나 생성하라.","ESSAY:수와 연산 서술형 문제를 하나 생성하라.");
  @Test void comparesApprovedPairs() throws Exception {
    Path file=Path.of("build/measurements/task7-model-comparison-pilot.tsv"); Files.createDirectories(file.getParent());
    StringBuilder out=new StringBuilder("generator\\tverifier\\tcaseId\\tgenMs\\tverifyMs\\tgenPrompt\\tgenCompletion\\tverifyPrompt\\tverifyCompletion\\n");
    for(String g:List.of("gpt-4o-mini","gpt-5.6-luna")) for(String v:List.of("gpt-4o-mini","gpt-5.6-luna")) for(String item:CASES){
      String[] p=item.split(":",2);
      try { Timed a=call(g,"문제를 생성한다.",p[1]); Timed b=call(v,"문제의 수학 오류를 점검한다.",a.text());
        out.append(g).append('\t').append(v).append('\t').append(p[0]).append('\t').append(a.ms).append('\t').append(b.ms).append('\t').append(a.r.promptTokens()).append('\t').append(a.r.completionTokens()).append('\t').append(b.r.promptTokens()).append('\t').append(b.r.completionTokens()).append('\n');
      } catch (RuntimeException failure) { out.append(g).append('\t').append(v).append('\t').append(p[0]).append("\tERROR\t").append(failure.getClass().getSimpleName()).append('\n'); }
    }
    Files.writeString(file,out.toString()); assertThat(out).contains("gpt-4o-mini\tgpt-5.6-luna");
  }
  private Timed call(String model,String system,String user){ OpenAiProperties p=new OpenAiProperties(System.getenv("OPENAI_API_KEY"),model,"minimal",1200,Duration.ofSeconds(60),0,Map.of()); OpenAiClientConfig c=new OpenAiClientConfig(); OpenAIClient raw=c.openAIClient(p); try { OpenAiChatModel m=c.openAiChatModel(raw,c.openAiChatOptions(p)); long s=System.nanoTime(); LlmResponse r=new OpenAiLlmClient(m,p).complete(system,List.of(ChatMessage.user(user))); return new Timed(r,(System.nanoTime()-s)/1_000_000); } finally { raw.close(); } }
  private record Timed(LlmResponse r,long ms){String text(){return r.text();}}
}

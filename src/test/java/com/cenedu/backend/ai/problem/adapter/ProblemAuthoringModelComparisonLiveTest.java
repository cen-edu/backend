package com.cenedu.backend.ai.problem.adapter;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*; import java.time.Duration; import java.util.*;
import com.cenedu.backend.ai.agent.ChatMessage; import com.cenedu.backend.ai.client.*; import com.openai.client.OpenAIClient;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.openai.*;

@EnabledIfEnvironmentVariable(named="OPENAI_API_KEY", matches=".+")
class ProblemAuthoringModelComparisonLiveTest {
  private static final List<String> PATHS=List.of("GENERAL","COMPREHENSIVE","SIMILAR","ADVANCED");
  @Test void comparesApprovedPairs() throws Exception {
    Path file=Path.of("build/measurements/task7-model-comparison-pilot.tsv"); Files.createDirectories(file.getParent());
    int start=Integer.getInteger("task7.batch.start",1), end=Integer.getInteger("task7.batch.end",5);
    String generatorFilter=System.getProperty("task7.generator", "");
    String verifierFilter=System.getProperty("task7.verifier", "");
    String pathFilter=System.getProperty("task7.path", "");
    List<String> generators=selected(List.of("gpt-4o-mini","gpt-5.6-luna"), generatorFilter);
    List<String> verifiers=selected(List.of("gpt-4o-mini","gpt-5.6-luna"), verifierFilter);
    List<String> paths=selected(PATHS, pathFilter);
    if(!Files.exists(file) || Files.size(file)==0) Files.writeString(file,
            "generator\tverifier\tcaseId\tgenMs\tverifyMs\tgenPrompt\tgenCompletion\tverifyPrompt\tverifyCompletion\n",
            StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    Map<String, ModelClient> clients=new HashMap<>();
    java.util.stream.Stream.concat(generators.stream(), verifiers.stream()).distinct()
            .forEach(model -> clients.put(model, open(model)));
    int completed=0;
    try {
    for(String g:generators) for(String v:verifiers) for(String path:paths) for(int i=start;i<=end;i++){
      String caseId=path+"-"+i; String prompt="중학교 1학년 수학의 "+path+" 출제 경로 표본 "+i+"번 문제를 생성하라. 정수와 일차방정식을 활용하라.";
      String row;
      try { Timed a=clients.get(g).call("문제를 생성한다.",prompt); Timed b=clients.get(v).call("문제의 수학 오류를 점검한다.",a.text());
        row=g+'\t'+v+'\t'+caseId+'\t'+a.ms+'\t'+b.ms+'\t'+a.r.promptTokens()+'\t'+a.r.completionTokens()+'\t'+b.r.promptTokens()+'\t'+b.r.completionTokens()+'\n';
      } catch (RuntimeException failure) { String message=String.valueOf(failure.getMessage()).replaceAll("\\s+", " "); row=g+'\t'+v+'\t'+caseId+"\tERROR\t"+classify(message)+'\t'+message+'\n'; }
      Files.writeString(file,row,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
      completed++;
    }
    } finally { clients.values().forEach(ModelClient::close); }
    assertThat(completed).isEqualTo(generators.size()*verifiers.size()*paths.size()*(end-start+1));
  }
  private List<String> selected(List<String> allowed,String filter){ if(filter.isBlank()) return allowed; assertThat(allowed).contains(filter); return List.of(filter); }
  private ModelClient open(String model){ String effort=model.startsWith("gpt-5")?"medium":"minimal"; OpenAiProperties p=new OpenAiProperties(System.getenv("OPENAI_API_KEY"),model,effort,1200,Duration.ofSeconds(30),0,Map.of()); OpenAiClientConfig c=new OpenAiClientConfig(); OpenAIClient raw=c.openAIClient(p); return new ModelClient(raw,new OpenAiLlmClient(c.openAiChatModel(raw,c.openAiChatOptions(p)),p)); }
  private String classify(String message){ String m=message.toLowerCase(Locale.ROOT); if(m.contains("timeout")||m.contains("timed out")) return "TIMEOUT"; if(m.contains("429")||m.contains("rate limit")) return "RATE_LIMIT"; if(m.contains("connection")||m.contains("connect")) return "CONNECTION"; if(m.contains("400")||m.contains("bad request")) return "BAD_REQUEST"; return "UNKNOWN"; }
  private record Timed(LlmResponse r,long ms){String text(){return r.text();}}
  private record ModelClient(OpenAIClient raw, OpenAiLlmClient client){ Timed call(String s,String u){long t=System.nanoTime(); return new Timed(client.complete(s,List.of(ChatMessage.user(u))),(System.nanoTime()-t)/1_000_000);} void close(){raw.close();} }
}

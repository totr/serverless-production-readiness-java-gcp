/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package services;

import static dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocument;

import com.google.cloud.vertexai.api.CountTokensResponse;
import com.google.cloud.vertexai.generativeai.GenerativeModel;
import com.google.cloud.vertexai.VertexAI;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.TextDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.service.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles(value = "test")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_PROJECT_ID", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_LOCATION", matches = ".*")
@EnabledIfEnvironmentVariable(named = "VERTEX_AI_GEMINI_MODEL", matches = ".*")
public class SummarizationTests {

    @Autowired
    private VertexAiGeminiChatModel chatModel;

    // @Value("classpath:/books/The_Wasteland-TSEliot-public.txt")
    @Value("classpath:/books/berlin-wikipedia.txt")
    private Path resource;

    public static final String DEFAULT_CHUNK_SIZE = "5000";
    private static final int CHUNK_SIZE = Integer.parseInt(System.getenv().getOrDefault("CHUNK_SIZE", DEFAULT_CHUNK_SIZE));  // Number of words in each chunk
    public static final String DEFAULT_OVERLAP_SIZE = "500";
    private static final int OVERLAP_SIZE = Integer.parseInt(System.getenv().getOrDefault("OVERLAP_SIZE", DEFAULT_OVERLAP_SIZE)); // Number of words between chunks

    /*
        AI Services for summarization helper methods
        @see https://docs.langchain4j.dev/tutorials/ai-services/
    */

    // --- Assistant Summarization using Stuffing ---
    public interface StuffingSummarizationAssistant {
        @SystemMessage("""
        You are a helpful AI assistant.
        You are an AI assistant that helps people summarize information.
        Your name is Gemini
        You should reply to the user's request with your name and also in the style of a literary critic
        Strictly ignore Project Gutenberg & ignore copyright notice in summary output.
        """)
        @UserMessage("""
        Please provide a concise summary in strictly no more than 10 one sentence bullet points,
        starting with an introduction and ending with a conclusion, of the following text
                  TEXT: {{content}}
        """)
        String summarize(@V("content") String content);
    }

    public interface FinalSummarizationAssistant {
        @SystemMessage("""
        You are a helpful AI assistant.
        You are an AI assistant that helps people summarize information.
        Your name is Gemini
        You should reply to the user's request with your name and also in the style of a literary critic
        Strictly ignore Project Gutenberg & ignore copyright notice in summary output.
        """)
        @UserMessage("""
        Please provide a concise summary in strictly no more than 10 one sentence bullet points,
        starting with an introduction and ending with a conclusion, of the following text delimited by triple backquotes.
      
        ```Text:{{content}}```
      
        Output starts with SUMMARY:
        """)
        String summarize(@V("content") String content);
    }

    public interface ChunkSummarizationAssistant {
        @SystemMessage("""
        You are a helpful AI assistant.
        You are an AI assistant that helps people summarize information.
        Your name is Gemini
        You should reply to the user's request with your name and also in the style of a literary critic
        Strictly ignore Project Gutenberg & ignore copyright notice in summary output.
        """)
        @UserMessage("""
        Taking the following context delimited by triple backquotes into consideration

        ```{{context}}```

        Write a concise summary of the following text delimited by triple backquotes.

        ```{{content}}```

        Output starts with CONCISE SUB-SUMMARY:

        """)
        String summarize(@V("context") String context, @V("content") String content);
    }

    /*
        Summarization tests
    */

    // --- Summarization using Stuffing ---
    @Order(1)
    @Test
    public void summarizationStuffTest() {
        // load the document
        Document document = loadDocument(resource, new TextDocumentParser());

        // summarize the document with the help of the StuffingSummarizationAssistant
        long start = System.currentTimeMillis();
        StuffingSummarizationAssistant assistant = AiServices.create(StuffingSummarizationAssistant.class, chatModel);
        String response = assistant.summarize(document.text());

        System.out.printf(response);
        System.out.printf("\nSummarization (stuffing test) took %d milliseconds", (System.currentTimeMillis() - start));
    }

    // --- Summarization using MapReduce  with overlapping chunks ---
    @Order(2)
    @Test
    public void summarizationMapReduceChunksTest() throws Exception {
        // load the document
        Document document = loadDocument(resource, new TextDocumentParser());

        // Overlap window size between chunks set to OVERLAP_SIZE - can be configured
        // from 0 - text.length()
        DocumentSplitter splitter = new DocumentByParagraphSplitter(CHUNK_SIZE, OVERLAP_SIZE);
        List<TextSegment> segments = splitter.split(document);

        // map each chunk to an individual chunk summarization thread
        // aggregate all chunk summaries
        long start = System.currentTimeMillis();
        List<CompletableFuture<Map<Integer, String>>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Map<Integer, String> resultMap = new TreeMap<>(); // TreeMap to automatically sort by key

        for(int i = 0; i < segments.size(); i++) {
            int index = i;
            CompletableFuture<Map<Integer, String>> future = CompletableFuture
                .supplyAsync(() -> summarizeChunk(index, segments.get(index).text()), executor);
            futures.add(future);
        }

        // Wait for all futures to complete and collect the results in resultMap
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenAccept(v -> futures.forEach(f -> f.thenAccept(resultMap::putAll)));

        allDone.get(); // Wait for all processing to complete

        // build the final context string from the sorted entries in the resultMap
        StringBuilder contextBuilder = new StringBuilder();
        for (Map.Entry<Integer, String> entry : resultMap.entrySet()) {
            contextBuilder.append(entry.getValue()).append("\n");
        }

        // build the final summary
        String content = contextBuilder.toString();
        String output = buildFinalSummary(content);

        System.out.println(output);
        System.out.printf("\nChunks: %d, Chunk size: %d, Overlap size: %d", segments.size(), CHUNK_SIZE, OVERLAP_SIZE);
        System.out.printf("\nSummarization (map-reduce) took %d milliseconds", (System.currentTimeMillis() - start));

        executor.shutdown(); // Shutdown the executor
    }

    // --- Summarization using Refine with overlapping chunks ---
    @Order(3)
    @Test
    public void summarizationRefineWithChunksTest(){
        // load the document
        Document document = loadDocument(resource, new TextDocumentParser());

        // Overlap window size between chunks set to OVERLAP_SIZE - can be configured
        // from 0 - text.length()
        DocumentSplitter splitter = new DocumentByParagraphSplitter(CHUNK_SIZE, OVERLAP_SIZE);
        List<TextSegment> chunks = splitter.split(document);

        // process each individual chunk in order
        // summary refined in each step by adding the summary of the current chunk
        long start = System.currentTimeMillis();
        StringBuilder context = new StringBuilder();
        chunks.forEach(segment -> summarizeChunk(context, segment.text()));

        // process the final summary  of the text
        String output = buildFinalSummary(context.toString());

        System.out.println(output);
        System.out.printf("\nChunks: %d, Chunk size: %d, Overlap size: %d", chunks.size(), CHUNK_SIZE, OVERLAP_SIZE);
        System.out.printf("\nSummarization (refine, with chunking test) took %d milliseconds", (System.currentTimeMillis() - start));
    }

    /*
        Summarization helper methods
     */

    // Final summarization process for text using rge FinalSummarizationAssistant
    private String buildFinalSummary(String content) {
        long start = System.currentTimeMillis();

        FinalSummarizationAssistant assistant = AiServices.create(FinalSummarizationAssistant.class, chatModel);
        String response = assistant.summarize(content);

        System.out.println(content+"\n\n");
        System.out.println("Summarization (final summary) took " + (System.currentTimeMillis() - start) + " milliseconds");
        return response;
    }

    // Summarize a chunk using the ChunkSummarizationAssistant
    private void summarizeChunk(StringBuilder context, String segment) {
        long start = System.currentTimeMillis();

        ChunkSummarizationAssistant assistant = AiServices.create(ChunkSummarizationAssistant.class, chatModel);
        String response = assistant.summarize(context.toString(), segment);

        System.out.println(response);
        System.out.println("Summarization (single chunk) took " + (System.currentTimeMillis() - start) + " milliseconds");
        context.append("\n").append(response);
    }

    // Summarize a chunk using the index in the list of parsed chunks
    // as correlation ID for parallel processing
    private Map<Integer, String> summarizeChunk(Integer index, String chunk) {
        Map<Integer, String> outputWithIndex = new HashMap<>();
        StringBuilder content = new StringBuilder();

        summarizeChunk(content, chunk);

        outputWithIndex.put(index, content.toString());
        return outputWithIndex;
    }

    @Order(4)
    @Test
    public void testGetTokenCount() throws IOException {
        try (VertexAI vertexAI = new VertexAI(System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"),
                                              System.getenv("VERTEX_AI_GEMINI_LOCATION"))) {
            GenerativeModel model = new GenerativeModel(System.getenv("VERTEX_AI_GEMINI_MODEL"), vertexAI);

            // load the document
            Document document = loadDocument(resource, new TextDocumentParser());

            // count tokens using the model of choice
            CountTokensResponse response = model.countTokens(document.text());
            System.out.printf("\nTotal tokens in text: %d", response.getTotalTokens());
            System.out.printf("\nTotal billable characters in text: %d", response.getTotalBillableCharacters());
        }
    }

    // --- set the test configuration up ---
    @SpringBootConfiguration
    public static class TestConfiguration {
        @Bean
        public VertexAiGeminiChatModel vertexAiChatModel() {
            return VertexAiGeminiChatModel.builder()
                .project(System.getenv("VERTEX_AI_GEMINI_PROJECT_ID"))
                .location(System.getenv("VERTEX_AI_GEMINI_LOCATION"))
                .modelName(System.getenv("VERTEX_AI_GEMINI_MODEL"))
                .temperature(0.2f)
                .maxOutputTokens(8192)
                .build();
        }
    }
}

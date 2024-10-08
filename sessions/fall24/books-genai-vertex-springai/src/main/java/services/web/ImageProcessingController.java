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
package services.web;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import javax.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Description;
import org.springframework.context.annotation.ImportRuntimeHints;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import services.actuator.StartupCheck;
import services.orchestration.BooksService;
import services.utility.RequestValidationUtility;

/**
 * ImageProcessingController is a Spring Boot REST controller that listens for Cloud Storage events
 * and processes the uploaded images.
 * Controller is responsible for:
 * 1. Receiving the Cloud Storage event
 * 2. Extracting the image metadata
 * 3. Extracting the text from the image
 * 4. Extracting the book details from the text
 * 5. Extracting the book summary from the database
 * 6. Calling the LLM to generate a report for the book including a book summary
 * and the up-to-date availability in the Bookstore
 * 7. Uses the BookStoreService to check the availability of the book, invoked by the LLM
 * using the function calling feature
 * 8. Saving the image metadata in Firestore
 */
@RestController
@RequestMapping("/images")
@ImportRuntimeHints(ImageProcessingController.BookStoreServiceRuntimeHints.class)
public class ImageProcessingController {
    private static final Logger logger = LoggerFactory.getLogger(ImageProcessingController.class);

    private final BooksService booksService;

    public ImageProcessingController(BooksService booksService) {
        this.booksService = booksService;
    }

    @PostConstruct
    public void init() {
        logger.info("BookImagesApplication: ImageProcessingController Post Construct Initializer {}",
                new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis())));
        logger.info("BookImagesApplication: ImageProcessingController Post Construct - StartupCheck can be enabled");

        StartupCheck.up();
    }

    @GetMapping("start")
    String start(){
        logger.info("BookImagesApplication: ImageProcessingController - Executed start endpoint request {}",
                new SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date(System.currentTimeMillis())));
        return "ImageProcessingController started";
    }

    @RequestMapping(value = "", method = RequestMethod.POST)
    public ResponseEntity<String> receiveMessage(@RequestBody Map<String, Object> body,
                                                 @RequestHeader Map<String, String> headers) throws IOException, InterruptedException, ExecutionException {
        String errorMsg = RequestValidationUtility.validateRequest(body,headers);
        if (!errorMsg.isBlank()) {
            return new ResponseEntity<>(errorMsg, HttpStatus.BAD_REQUEST);
        }

        // get the file info
        String fileName = (String)body.get("name");
        String bucketName = (String)body.get("bucket");
        logger.info("New picture uploaded to Cloud Storage {} in bucket {}", fileName, bucketName);

        // analyze the image in the CLoud Storage bucket
        booksService.analyzeImage(bucketName, fileName);

        return new ResponseEntity<>(HttpStatus.OK);
    }

    @Bean
    @Description("Get availability of book in the book store")
    public Function<BookStoreService.Request, BookStoreService.Response> bookStoreAvailability() {
        return new BookStoreService();
    }

    /**
     * BookStoreService is a function that checks the availability of a book in the bookstore.
     * Invoked by the LLM using the function calling feature.
     */
    public static class BookStoreService
        implements Function<BookStoreService.Request, BookStoreService.Response> {

        @JsonInclude(Include.NON_NULL)
        @JsonClassDescription("BookStore API Request")
        public record Request(
            @JsonProperty(required = true, value = "title") @JsonPropertyDescription("The title of the book") String title,
            @JsonProperty(required = true, value = "author") @JsonPropertyDescription("The author of the book") String author) {
        }
        @JsonInclude(Include.NON_NULL)
        public record Response(String title, String author, String availability) {
        }

        @Override
        public Response apply(Request request) {
            logger.info("Called getBookAvailability({}, {})", request.title(), request.author());
            return new Response(request.title(), request.author(), "The book is available for purchase in the book store in paperback format.");
        }
    }

    public static class BookStoreServiceRuntimeHints implements RuntimeHintsRegistrar {
        @Override
        public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
            // Register method for reflection
            var mcs = MemberCategory.values();
            hints.reflection().registerType(BookStoreService.Request.class, mcs);
            hints.reflection().registerType(BookStoreService.Response.class, mcs);
        }
    }
}

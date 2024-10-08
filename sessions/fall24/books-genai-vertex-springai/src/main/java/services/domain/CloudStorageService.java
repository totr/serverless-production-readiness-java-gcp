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
package services.domain;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CloudStorageService {
    public BufferedReader readFile(String bucket, String fileName) {
        // Create a Storage client.
        Storage storage = StorageOptions.getDefaultInstance().getService();

        // Get the blob.
        Blob blob = storage.get(BlobId.of(bucket, fileName));

        ReadableByteChannel channel = blob.reader();
        InputStreamReader isr = new InputStreamReader(Channels.newInputStream(channel));
        BufferedReader br = new BufferedReader(isr);
        return br;
    }

    public String readFileAsString(String bucket, String fileName) {
        // Create a Storage client.
        Storage storage = StorageOptions.getDefaultInstance().getService();

        // Get the blob.
        Blob blob = storage.get(BlobId.of(bucket, fileName));

        return (blob == null ? "" : new String(blob.getContent(), StandardCharsets.UTF_8));
    }

    public List<Document> readFileAsDocument(String bucket, String fileName) {
        // Create a Storage client.
        Storage storage = StorageOptions.getDefaultInstance().getService();

        // Get the blob.
        Blob blob = storage.get(BlobId.of(bucket, fileName));

        // return the content as a document
        return List.of(new Document(new String(blob.getContent(), StandardCharsets.UTF_8)));
    }

    public byte[]  readFileAsByteString(String bucket, String fileName) throws IOException {
        // Create a Storage client.
        Storage storage = StorageOptions.getDefaultInstance().getService();

        // Get the blob.
        Blob blob = storage.get(BlobId.of(bucket, fileName));

        // Use ByteArrayOutputStream to efficiently read bytes
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ReadableByteChannel channel = blob.reader()) {
            byte[] buffer = new byte[1024]; // Read data in chunks
            int bytesRead;
            while ((bytesRead = channel.read(ByteBuffer.wrap(buffer))) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }

        return outputStream.toByteArray(); // Convert bytes to String using UTF-8
    }
}

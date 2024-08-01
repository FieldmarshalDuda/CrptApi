import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class CrptApi {
    private final long requestIntervalMillis;
    private final int requestLimit;
    private int requestCount = 0;
    private long lastRequestTimestamp = 0;
    private final Object lock = new Object();

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        if (requestLimit <= 0) {
            throw new IllegalArgumentException("requestLimit must be positive.");
        }
        this.requestIntervalMillis = timeUnit.toMillis(1);
        this.requestLimit = requestLimit;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    public void createDocument(Document document, String signature) throws IOException, InterruptedException {
        synchronized (lock) {
            long currentTime = System.currentTimeMillis();
            // Сброс счетчика по исходу времени
            if (currentTime - lastRequestTimestamp >= requestIntervalMillis) {
                requestCount = 0;
                lastRequestTimestamp = currentTime;
            }

            // Блокировка, до тех пор пока не освободится запрос
            while (requestCount >= requestLimit) {
                long sleepTime = requestIntervalMillis - (currentTime - lastRequestTimestamp);
                if (sleepTime > 0) {
                    lock.wait(sleepTime);
                }
                // После ожидания обновляется время и счетчик
                currentTime = System.currentTimeMillis();
                if (currentTime - lastRequestTimestamp >= requestIntervalMillis) {
                    requestCount = 0;
                    lastRequestTimestamp = currentTime;
                }
            }
            requestCount++;
        }

        sendDocumentToApi(document, signature);
    }

    private void sendDocumentToApi(Document document, String signature) throws IOException, InterruptedException {
        String json = objectMapper.writeValueAsString(document);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Обработка ответа
        System.out.println("Response status code: " + response.statusCode());
        System.out.println("Response body: " + response.body());
    }

    // Внутренний класс для документа
    public static class Document {
        private Description description;
        private String doc_id;
        private String doc_status;
        private String doc_type;
        private boolean importRequest;
        private String owner_inn;
        private String participant_inn;
        private String producer_inn;
        private String production_date;
        private String production_type;
        private Product[] products;
        private String reg_date;
        private String reg_number;

        // Конструктор

        public Document(String doc_id, String doc_status, String doc_type, boolean importRequest,
                        String owner_inn, String participant_inn, String producer_inn,
                        String production_date, String production_type,
                        Product[] products, String reg_date, String reg_number) {
            this.description = new Description(participant_inn);
            this.doc_id = doc_id;
            this.doc_status = doc_status;
            this.doc_type = doc_type;
            this.importRequest = importRequest;
            this.owner_inn = owner_inn;
            this.participant_inn = participant_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.production_type = production_type;
            this.products = products;
            this.reg_date = reg_date;
            this.reg_number = reg_number;
        }


        private static class Description {
            private String participantInn;

            public Description(String participantInn) {
                this.participantInn = participantInn;
            }
        }

    }

    // Внутренний класс для продукта
    public static class Product {
        private String certificate_document;
        private String certificate_document_date;
        private String certificate_document_number;
        private String owner_inn;
        private String producer_inn;
        private String production_date;
        private String tnved_code;
        private String uit_code;
        private String uitu_code;

        // Конструктор

        public Product(String certificate_document, String certificate_document_date,
                       String certificate_document_number, String owner_inn,
                       String producer_inn, String production_date,
                       String tnved_code, String uit_code, String uitu_code) {
            this.certificate_document = certificate_document;
            this.certificate_document_date = certificate_document_date;
            this.certificate_document_number = certificate_document_number;
            this.owner_inn = owner_inn;
            this.producer_inn = producer_inn;
            this.production_date = production_date;
            this.tnved_code = tnved_code;
            this.uit_code = uit_code;
            this.uitu_code = uitu_code;
        }
    }
}

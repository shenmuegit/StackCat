package com.stackcat.agent.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;

/**
 * @author zhenzijun
 */
@Slf4j
public class MethodTrackingBridge {

    private static final class CallHistoryHolder {
        // ThreadLocal data collection (lazy initialization to avoid class loading issues)
        private static final ThreadLocal<List<MethodCallInfo>> CALL_HISTORY = ThreadLocal.withInitial(ArrayList::new);
    }

    // Lazy initialization of ThreadLocal variables
    private static ThreadLocal<List<MethodCallInfo>> getCallHistory() {
        return CallHistoryHolder.CALL_HISTORY;
    }

    private static final class CurrentDepthHolder {
        private static final ThreadLocal<Integer> CURRENT_DEPTH = ThreadLocal.withInitial(() -> 0);
    }

    private static ThreadLocal<Integer> getCurrentDepthThreadLocal() {
        // Use withInitial instead of anonymous class to avoid recursion issues
        return CurrentDepthHolder.CURRENT_DEPTH;
    }

    private static final class RequestIdHolder {
        private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();
    }

    private static ThreadLocal<String> getRequestIdThreadLocal() {
        return RequestIdHolder.REQUEST_ID;
    }

    private static final class RequestHeadersHolder {
        private static final ThreadLocal<Map<String, String>> REQUEST_HEADERS = new ThreadLocal<>();
    }

    private static ThreadLocal<Map<String, String>> getRequestHeadersThreadLocal() {
        return RequestHeadersHolder.REQUEST_HEADERS;
    }

    private static final class SequenceCounterHolder {
        private static final ThreadLocal<AtomicInteger> SEQUENCE_COUNTER = ThreadLocal.withInitial(() -> new AtomicInteger(0));
    }

    private static ThreadLocal<AtomicInteger> getSequenceCounter() {
        return SequenceCounterHolder.SEQUENCE_COUNTER;
    }

    private static final class RequestStartTimeHolder {
        private static final ThreadLocal<LocalDateTime> REQUEST_START_TIME = new ThreadLocal<>();
    }

    private static ThreadLocal<LocalDateTime> getRequestStartTime() {
        return RequestStartTimeHolder.REQUEST_START_TIME;
    }

    private static final class SqlStatementsHolder {
        private static final ThreadLocal<List<String>> SQL_STATEMENTS = ThreadLocal.withInitial(ArrayList::new);
    }

    private static ThreadLocal<List<String>> getSqlStatements() {
        return SqlStatementsHolder.SQL_STATEMENTS;
    }

    // Capture SQL statement from Connection.prepareStatement()
    public static void captureSqlStatement(String sql) {
        try {
            if (sql != null && !sql.trim().isEmpty()) {
                List<String> sqlList = getSqlStatements().get();
                if (sqlList == null) {
                    sqlList = getSqlStatements().get();
                }
                sqlList.add(sql);
                log.debug("Captured SQL: {}...", sql.substring(0, Math.min(100, sql.length())));
            }
        } catch (Exception e) {
            log.error("Error capturing SQL: {}", e.getMessage());
        }
    }

    // Get the most recent SQL statement and clear it from the list
    public static String getAndClearRecentSql() {
        try {
            List<String> sqlList = getSqlStatements().get();
            if (sqlList != null && !sqlList.isEmpty()) {
                // Get the last SQL statement
                return sqlList.removeLast();
            }
        } catch (Exception e) {
            log.error("Error getting recent SQL: {}", e.getMessage());
        }
        return null;
    }

    // HTTP client and async sending (lazy initialization)
    private static volatile HttpClient httpClient;
    private static volatile BlockingQueue<BatchData> sendQueue;
    private static volatile boolean senderThreadStarted = false;

    private static BlockingQueue<BatchData> getSendQueue() {
        if (sendQueue == null) {
            synchronized (MethodTrackingBridge.class) {
                if (sendQueue == null) {
                    sendQueue = new LinkedBlockingQueue<>();
                }
            }
        }
        return sendQueue;
    }

    private static HttpClient getHttpClient() {
        if (httpClient == null) {
            synchronized (MethodTrackingBridge.class) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .build();
                }
            }
        }
        return httpClient;
    }

    // Configuration (read from system properties)
    private static final long DEFAULT_INTERVAL = 1000; // 1 second
    private static final int DEFAULT_BATCH_SIZE = 10;
    private static final String DEFAULT_API_URL = "http://localhost:8080/api/tracking/batch";

    // Configuration variables (removed static initialization to avoid class loading issues)
    // These will be read from system properties lazily when needed

    // Lazy initialization of configuration
    private static void ensureConfigLoaded() {
        // Configuration is loaded lazily when first needed
    }

    private static long getAsyncInterval() {
        String intervalStr = System.getProperty("stackcat.agent.async.interval");
        if (intervalStr != null) {
            try {
                return Long.parseLong(intervalStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        return DEFAULT_INTERVAL;
    }

    private static int getAsyncBatchSize() {
        String batchSizeStr = System.getProperty("stackcat.agent.async.batch-size");
        if (batchSizeStr != null) {
            try {
                return Integer.parseInt(batchSizeStr);
            } catch (NumberFormatException e) {
                // Use default
            }
        }
        return DEFAULT_BATCH_SIZE;
    }

    private static String getApiUrl() {
        String urlStr = System.getProperty("stackcat.agent.api.url");
        return urlStr != null ? urlStr : DEFAULT_API_URL;
    }

    // Lazy initialization of sender thread
    private static void ensureSenderThread() {
        if (!senderThreadStarted) {
            synchronized (MethodTrackingBridge.class) {
                if (!senderThreadStarted) {
                    startSenderThread();
                }
            }
        }
    }

    // Internal class for method call information
    private static class MethodCallInfo {
        String className;
        String methodName;
        String packageName;
        int depth;
        int sequence;
        LocalDateTime callTime;
        int parentIndex; // Index of parent call in the list
        String query; // SQL/JPQL query string for Repository methods (optional)

        MethodCallInfo(String className, String methodName, int depth, int sequence, LocalDateTime callTime, int parentIndex) {
            this(className, methodName, depth, sequence, callTime, parentIndex, null);
        }

        MethodCallInfo(String className, String methodName, int depth, int sequence, LocalDateTime callTime, int parentIndex, String query) {
            this.className = className;
            this.methodName = methodName;
            this.depth = depth;
            this.sequence = sequence;
            this.callTime = callTime;
            this.parentIndex = parentIndex;
            this.query = query;

            // Extract package name
            int lastDot = className.lastIndexOf('.');
            this.packageName = lastDot > 0 ? className.substring(0, lastDot) : "";
        }
    }

    // Batch data for sending
    private static class BatchData {
        String requestId;
        Map<String, String> headers;
        LocalDateTime startTime;
        LocalDateTime endTime;
        List<MethodCallInfo> methodCalls;

        BatchData(String requestId, Map<String, String> headers, LocalDateTime startTime,
                  LocalDateTime endTime, List<MethodCallInfo> methodCalls) {
            this.requestId = requestId;
            this.headers = headers != null ? new HashMap<>(headers) : new HashMap<>();
            this.startTime = startTime;
            this.endTime = endTime;
            this.methodCalls = new ArrayList<>(methodCalls);
        }
    }

    private static synchronized void startSenderThread() {
        if (!senderThreadStarted) {
            Thread senderThread = new Thread(() -> {
                List<BatchData> batch = new ArrayList<>();
                long lastSendTime = System.currentTimeMillis();

                while (true) {
                    try {
                        // Try to take one item from queue (non-blocking)
                        BatchData data = getSendQueue().poll();
                        if (data != null) {
                            batch.add(data);
                        }

                        long currentTime = System.currentTimeMillis();
                        boolean shouldSend = isShouldSend(batch, currentTime, lastSendTime);

                        if (shouldSend && !batch.isEmpty()) {
                            sendBatch(batch);
                            batch.clear();
                            lastSendTime = currentTime;
                        }

                        // Sleep a bit to avoid busy waiting
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        // Log error but continue
                        log.error("Error in sender thread: {}", e.getMessage(), e);
                    }
                }
            }, "StackCat-Sender-Thread");
            senderThread.setDaemon(true);
            senderThread.start();
            senderThreadStarted = true;
        }
    }

    private static boolean isShouldSend(List<BatchData> batch, long currentTime, long lastSendTime) {
        boolean shouldSend = false;

        // Check if should send: time interval or batch size
        int batchSize = getAsyncBatchSize();
        long interval = getAsyncInterval();
        if (batch.size() >= batchSize) {
            shouldSend = true;
        } else if (!batch.isEmpty() && (currentTime - lastSendTime) >= interval) {
            shouldSend = true;
        }
        return shouldSend;
    }

    public static void startRequest(String requestId, Map<String, String> headers) {
        log.debug("Start request: requestId={}, headers={}", requestId, headers != null ? headers.size() : 0);

        // If requestId is null or empty, don't start tracking (skip this request)
        if (requestId == null || requestId.isEmpty()) {
            return;
        }

        getRequestIdThreadLocal().set(requestId);
        getRequestHeadersThreadLocal().set(headers != null ? new HashMap<>(headers) : new HashMap<>());
        getCallHistory().set(new ArrayList<>());
        getCurrentDepthThreadLocal().set(0);
        getSequenceCounter().set(new AtomicInteger(0));
        getRequestStartTime().set(LocalDateTime.now());
    }

    // Start request tracking from Controller method entry point
    // This method gets requestId and headers from RequestContextHolder
    public static void startRequestFromController() {
        try {

            // Get requestId from RequestContextHolder
            String requestId = getRequestId();

            // Get headers from RequestContextHolder
            Map<String, String> headers = getRequestHeaders();

            // Call startRequest with the obtained values
            startRequest(requestId, headers);
        } catch (Exception e) {
            log.error("Error in startRequestFromController: {}", e.getMessage(), e);
        }
    }

    public static void endRequest() {
        try {
            log.debug("End request called");
            // Ensure sender thread is started
            ensureSenderThread();

            String reqId = getRequestId();
            if (reqId == null || reqId.isEmpty()) {
                return;
            }

            List<MethodCallInfo> calls = getCallHistory().get();
            if (calls == null || calls.isEmpty()) {
                return;
            }

            log.debug("End request: requestId={}, methodCalls={}", reqId, calls.size());
            Map<String, String> headers = getRequestHeadersThreadLocal().get();
            LocalDateTime startTime = getRequestStartTime().get();
            LocalDateTime endTime = LocalDateTime.now();

            // Add to send queue
            BatchData batchData = new BatchData(reqId, headers, startTime, endTime, calls);
            if (!getSendQueue().offer(batchData)) {
                log.warn("Failed to enqueue batch data for requestId={}", reqId);
            }
        } finally {
            // Clean up ThreadLocal
            getRequestIdThreadLocal().remove();
            getRequestHeadersThreadLocal().remove();
            getCallHistory().remove();
            getCurrentDepthThreadLocal().remove();
            getSequenceCounter().remove();
            getRequestStartTime().remove();
            getSqlStatements().remove();
        }
    }

    public static void recordMethodCall(String className, String methodName, int depth) {
        recordMethodCall(className, methodName, depth, null);
    }

    public static void recordMethodCall(String className, String methodName, int depth, String query) {
        // Check if request tracking is active
        String reqId = getRequestId();
        if (reqId == null || reqId.isEmpty()) {
            // No active request tracking, skip this call
            return;
        }

        List<MethodCallInfo> calls = getCallHistory().get();
        if (calls == null) {
            // Initialize call history if not exists
            calls = getCallHistory().get();
            if (calls == null) {
                return;
            }
        }

        int sequence = getSequenceCounter().get().getAndIncrement();
        LocalDateTime callTime = LocalDateTime.now();

        // Find parent index based on depth
        int parentIndex = -1;
        if (depth > 0) {
            // Find the last call at depth - 1
            for (int i = calls.size() - 1; i >= 0; i--) {
                if (calls.get(i).depth == depth - 1) {
                    parentIndex = i;
                    break;
                }
            }
        }

        MethodCallInfo info = new MethodCallInfo(className, methodName, depth, sequence, callTime, parentIndex, query);
        calls.add(info);

        // Log all method calls for debugging (to see if Service methods are being called)
        log.debug("Recorded method call: {}.{} (depth={}, seq={}, total={})", className, methodName, depth, sequence, calls.size());
    }

    // Handle Repository method invocation from RepositoryMethodInvoker.doInvoke
    // This method is called by the instrumented doInvoke method
    // Parameters: method (java.lang.reflect.Method), repositoryInterface (Class<?>)
    public static void handleRepositoryMethodInvocation(Object method, Object repositoryInterface) {
        try {

            // Check if request tracking is active
            String reqId = getRequestId();
            if (reqId == null || reqId.isEmpty()) {
                return;
            }

            if (method == null || repositoryInterface == null) {
                return;
            }

            // Get method class using reflection
            java.lang.reflect.Method getDeclaringClassMethod = method.getClass().getMethod("getDeclaringClass");
            Class<?> declaringClass = (Class<?>) getDeclaringClassMethod.invoke(method);

            if (declaringClass == null) {
                return;
            }

            String declaringClassName = declaringClass.getName();

            // Get method name
            java.lang.reflect.Method getNameMethod = method.getClass().getMethod("getName");
            String methodName = (String) getNameMethod.invoke(method);

            // Get current depth
            int depth = getCurrentDepth();

            // Try to get actual SQL statement first (from captured SQL statements)
            String query = getAndClearRecentSql();

            // If no SQL captured, try to get @Query annotation value as fallback
            if (query == null || query.isEmpty()) {
                try {
                    // Get annotations from method
                    java.lang.reflect.Method getAnnotationsMethod = method.getClass().getMethod("getAnnotations");
                    Object[] annotations = (Object[]) getAnnotationsMethod.invoke(method);

                    if (annotations != null) {
                        // Look for @Query annotation
                        for (Object annotation : annotations) {
                            Class<?> annotationClass = annotation.getClass();
                            if ("org.springframework.data.jpa.repository.Query".equals(annotationClass.getName())) {
                                // Get value() method from @Query annotation
                                try {
                                    java.lang.reflect.Method valueMethod = annotationClass.getMethod("value");
                                    String[] values = (String[]) valueMethod.invoke(annotation);
                                    if (values != null && values.length > 0) {
                                        query = values[0]; // Get first query string (JPQL)
                                    }
                                } catch (Exception e) {
                                    // If single value, try getting it directly
                                    try {
                                        java.lang.reflect.Method valueMethod = annotationClass.getMethod("value");
                                        Object value = valueMethod.invoke(annotation);
                                        if (value != null) {
                                            query = value.toString();
                                        }
                                    } catch (Exception e2) {
                                        // Ignore
                                    }
                                }
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("Error extracting @Query annotation: {}", e.getMessage());
                }
            }

            // Record method call with query (actual SQL or JPQL from @Query annotation)
            // Note: If SQL is not available yet, query will be null and will be updated later
            recordMethodCall(declaringClassName, methodName, depth, query);

        } catch (Exception e) {
            log.error("Error in handleRepositoryMethodInvocation: {}", e.getMessage(), e);
        }
    }

    // Update the last Repository method call with SQL captured during execution
    // This is called after doInvoke() completes but before it returns
    public static void updateRepositoryMethodCallWithSql() {
        try {
            // Check if request tracking is active
            String reqId = getRequestId();
            if (reqId == null || reqId.isEmpty()) {
                return;
            }

            List<MethodCallInfo> calls = getCallHistory().get();
            if (calls == null || calls.isEmpty()) {
                return;
            }

            // Get the most recent SQL statement
            String sql = getAndClearRecentSql();
            if (sql == null || sql.isEmpty()) {
                return;
            }

            // Find the last Repository method call (checking from the end)
            for (int i = calls.size() - 1; i >= 0; i--) {
                MethodCallInfo call = calls.get(i);
                // Check if this is a Repository method call
                if (call.className.startsWith("com.stackcat.repository.")) {
                    // Update the query field if it's null or empty
                    if (call.query == null || call.query.isEmpty()) {
                        call.query = sql;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error in updateRepositoryMethodCallWithSql: {}", e.getMessage(), e);
        }
    }

    // Record MyBatis Mapper method entry
    // Called at the entry of SimpleExecutor.doUpdate/doQuery
    public static void recordMyBatisMethodEntry(Object mappedStatement, Object parameter) {
        try {
            log.info("Recording MyBatis method entry: mappedStatement={}, parameter={}",
                    mappedStatement != null ? mappedStatement.getClass().getName() : "null",
                    parameter != null ? parameter.getClass().getName() : "null");
            if (mappedStatement == null) {
                return;
            }
            // Get Mapper method ID using reflection
            // MappedStatement.getId() returns "com.example.mapper.UserMapper.selectById"
            java.lang.reflect.Method getIdMethod = mappedStatement.getClass().getMethod("getId");
            String mapperId = (String) getIdMethod.invoke(mappedStatement);
            if (mapperId == null || mapperId.isEmpty()) {
                return;
            }
            // 最后一个点后面就是methodName
            int lastDotIndex = mapperId.lastIndexOf('.');
            String methodName = lastDotIndex >= 0 && lastDotIndex < mapperId.length() - 1
                    ? mapperId.substring(lastDotIndex + 1)
                    : "";
            // Get current depth
            int depth = getCurrentDepth();

            // Check if there's a pending SQL statement from extractAndRecordMyBatisSql
            // (in case extractAndRecordMyBatisSql was called before recordMyBatisMethodEntry)
            String pendingSql = null;
            List<String> sqlList = getSqlStatements().get();
            if (sqlList != null && !sqlList.isEmpty()) {
                // Get the most recent SQL statement
                pendingSql = sqlList.removeLast();
                log.debug("Found pending SQL for MyBatis method: {}...",
                        pendingSql != null && pendingSql.length() > 100
                                ? pendingSql.substring(0, 100)
                                : pendingSql);
            }

            // Record method call (with SQL if available, otherwise will be updated after prepareStatement)
            recordMethodCall(mapperId, methodName, depth, pendingSql);

            log.debug("MyBatis method entry recorded: {} (with SQL: {})", mapperId, pendingSql != null);

        } catch (Exception e) {
            log.error("Error recording MyBatis method entry: {}", e.getMessage());
        }
    }

    // Extract SQL from MyBatis Statement and update the last method call immediately
    // Called after prepareStatement() returns
    public static void extractAndRecordMyBatisSql(Object statement) {
        try {

            if (statement == null) {
                log.warn("Statement is null, cannot extract SQL");
                return;
            }
            List<MethodCallInfo> calls = getCallHistory().get();

            // Extract complete SQL from Statement
            String completeSql = extractSqlFromStatement(statement);

            if (completeSql != null && !completeSql.isEmpty()) {
                // Update the last method call (most recent MyBatis mapper method)
                MethodCallInfo lastCall = calls.getLast();
                lastCall.query = completeSql;
                log.debug("Updated MyBatis SQL: {}...",
                        completeSql.substring(0, Math.min(100, completeSql.length())));
            } else {
                log.warn("Failed to extract SQL from MyBatis Statement");
            }

        } catch (Exception e) {
            log.error("Error extracting and recording MyBatis SQL: {}", e.getMessage());
        }
    }

    // Extract complete SQL from Statement (with parameters)
    // Supports MySQL PreparedStatement with HikariCP proxy unwrapping
    private static String extractSqlFromStatement(Object stmt) {
        try {
            // Add detailed logging for debugging
            // Check if it's MyBatis PreparedStatementLogger (could be wrapped in proxy)
            if (stmt.toString().contains("PreparedStatementLogger") ||
                    stmt.getClass().getName().startsWith("$Proxy")) {
                log.info("Detected possible MyBatis PreparedStatementLogger or Proxy");
                // Try to extract SQL from PreparedStatementLogger
                String sql = extractSqlFromPreparedStatementLogger(stmt);
                if (sql != null) {
                    return sql;
                }
            }

            // Step 1: Unwrap HikariCP proxy if present
            Object realStmt = unwrapHikariProxy(stmt);

            // Step 2: Try MySQL-specific asSql() method
            String sql = tryExtractFromMySQL(realStmt);
            if (sql != null) {
                return sql;
            }

            // Step 3: Fallback to toString() parsing
            String str = realStmt.toString();
            if (str.contains(":")) {
                return str.substring(str.indexOf(":") + 1).trim();
            }

            return str;

        } catch (Exception e) {
            log.error("Error extracting SQL from statement: {}", e.getMessage());
            return null;
        }
    }

    // Extract SQL from MyBatis PreparedStatementLogger
    private static String extractSqlFromPreparedStatementLogger(Object logger) {
        log.info("=== Extracting SQL from PreparedStatementLogger ===");
        log.info("Logger object class: {}", logger.getClass().getName());

        // Check if it's a proxy object (e.g., $Proxy88)
        if (logger.getClass().getName().startsWith("$Proxy") ||
                logger.getClass().getName().contains("Proxy")) {
            log.info("Detected proxy object, trying to get InvocationHandler");

            try {
                // For JDK dynamic proxy, use Proxy.getInvocationHandler() method
                Object invocationHandler = java.lang.reflect.Proxy.getInvocationHandler(logger);

                if (invocationHandler != null) {
                    log.info("Got InvocationHandler: {}", invocationHandler.getClass().getName());

                    // If the InvocationHandler is PreparedStatementLogger
                    if (invocationHandler.getClass().getName().contains("PreparedStatementLogger")) {
                        // Now get the statement field from PreparedStatementLogger
                        java.lang.reflect.Field statementField = invocationHandler.getClass().getDeclaredField("statement");
                        statementField.setAccessible(true);
                        Object realStatement = statementField.get(invocationHandler);

                        if (realStatement != null) {
                            log.info("Got real statement from PreparedStatementLogger: {}", realStatement.getClass().getName());
                            log.info("Real statement toString: {}", realStatement.toString());

                            // If it's HikariProxy, it should have SQL in toString
                            String stmtStr = realStatement.toString();
                            if (stmtStr.contains("wrapping")) {
                                // Extract SQL from string like "HikariProxyPreparedStatement@xxx wrapping SELECT * FROM ..."
                                int wrappingIndex = stmtStr.indexOf("wrapping");
                                if (wrappingIndex > 0) {
                                    String sql = stmtStr.substring(wrappingIndex + 9).trim(); // 9 = length of "wrapping "
                                    log.info("Extracted SQL from HikariProxy toString: {}", sql);
                                    return sql;
                                }
                            }

                            // Otherwise try to extract SQL normally
                            return tryExtractFromMySQL(realStatement);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error extracting from proxy object: {}", e.getMessage(), e);
            }
        }

        // If not a proxy, try direct field access
        try {
            // Method 1: Try to get statement field directly
            java.lang.reflect.Field statementField = logger.getClass().getDeclaredField("statement");
            statementField.setAccessible(true);
            Object realStatement = statementField.get(logger);

            if (realStatement != null) {
                log.info("Got real statement from PreparedStatementLogger (direct): {}", realStatement.getClass().getName());
                // Recursively extract SQL from the real statement
                return tryExtractFromMySQL(realStatement);
            }
        } catch (NoSuchFieldException e) {
            log.debug("No 'statement' field found in PreparedStatementLogger: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Failed to get statement field: {}", e.getMessage());
        }

        try {
            // Method 2: Try to get sql field (if exists)
            java.lang.reflect.Field sqlField = logger.getClass().getDeclaredField("sql");
            sqlField.setAccessible(true);
            String sql = (String) sqlField.get(logger);

            if (sql != null && !sql.isEmpty()) {
                log.info("Got SQL directly from sql field: {}", sql);
                return sql;
            }
        } catch (NoSuchFieldException e) {
            log.debug("No 'sql' field found in PreparedStatementLogger: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Failed to get sql field: {}", e.getMessage());
        }

        // Method 3: List all fields for debugging
        log.info("All fields in PreparedStatementLogger:");
        java.lang.reflect.Field[] allFields = logger.getClass().getDeclaredFields();
        for (java.lang.reflect.Field field : allFields) {
            field.setAccessible(true);
            try {
                Object value = field.get(logger);
                String valueStr = value != null ? value.toString() : "null";
                // Limit the output length for readability
                if (valueStr.length() > 100) {
                    valueStr = valueStr.substring(0, 100) + "...";
                }
                log.info("  Field '{}' (type: {}): {}",
                        field.getName(),
                        field.getType().getName(),
                        valueStr);
            } catch (Exception e) {
                log.debug("  Failed to access field '{}': {}", field.getName(), e.getMessage());
            }
        }

        // Method 4: Try to find methods that might return SQL
        log.info("Checking methods in PreparedStatementLogger:");
        java.lang.reflect.Method[] methods = logger.getClass().getDeclaredMethods();
        for (java.lang.reflect.Method method : methods) {
            if (method.getName().toLowerCase().contains("sql") ||
                    method.getName().toLowerCase().contains("query") ||
                    method.getName().toLowerCase().contains("statement")) {
                log.info("  Found potential SQL method: {} (params: {})",
                        method.getName(),
                        java.util.Arrays.toString(method.getParameterTypes()));
            }
        }

        log.warn("Failed to extract SQL from PreparedStatementLogger");
        return null;
    }

    // Unwrap HikariCP proxy to get the real Statement
    private static Object unwrapHikariProxy(Object stmt) {
        try {
            String className = stmt.getClass().getName();
            log.debug("Checking for proxy wrapper: {}", className);

            // Check if it's MyBatis PreparedStatementLogger (should be handled elsewhere)
            if (className.contains("PreparedStatementLogger")) {
                log.info("Detected PreparedStatementLogger in unwrapHikariProxy, should be handled in tryExtractFromMySQL or extractSqlFromPreparedStatementLogger");
                // Don't try to unwrap it here, return as-is
                return stmt;
            }

            // Check if it's a HikariCP proxy
            if (className.contains("HikariProxyPreparedStatement") ||
                    className.contains("HikariProxy")) {
                log.debug("Detected HikariCP proxy, unwrapping...");

                // HikariCP proxy has a 'delegate' field
                java.lang.reflect.Field delegateField = stmt.getClass().getDeclaredField("delegate");
                delegateField.setAccessible(true);
                Object realStmt = delegateField.get(stmt);

                log.debug("Unwrapped to: {}", realStmt.getClass().getName());
                return realStmt;
            }

            return stmt;

        } catch (Exception e) {
            log.debug("Failed to unwrap HikariCP proxy: {}", e.getMessage());
            return stmt;
        }
    }

    // Try to extract SQL from MySQL/PostgreSQL PreparedStatement
    private static String tryExtractFromMySQL(Object stmt) {
        try {
            String className = stmt.getClass().getName();

            log.debug("Attempting to extract SQL from statement: {}", className);

            // First handle MyBatis PreparedStatementLogger
            if (className.contains("org.apache.ibatis.logging.jdbc.PreparedStatementLogger")) {
                log.info("Processing MyBatis PreparedStatementLogger in tryExtractFromMySQL");

                // Try to get the internal statement field
                try {
                    java.lang.reflect.Field statementField = stmt.getClass().getDeclaredField("statement");
                    statementField.setAccessible(true);
                    Object realStatement = statementField.get(stmt);

                    if (realStatement != null) {
                        log.info("Successfully extracted real statement from PreparedStatementLogger");
                        log.info("Real statement class: {}", realStatement.getClass().getName());

                        // Recursively process the actual PreparedStatement
                        return tryExtractFromMySQL(realStatement);
                    } else {
                        log.warn("Real statement is null in PreparedStatementLogger");
                    }
                } catch (NoSuchFieldException e) {
                    log.warn("No 'statement' field found in PreparedStatementLogger, listing available fields:");

                    // List all available fields for debugging
                    java.lang.reflect.Field[] fields = stmt.getClass().getDeclaredFields();
                    for (java.lang.reflect.Field field : fields) {
                        log.info("  Available field: {} (type: {})", field.getName(), field.getType().getName());
                    }
                } catch (Exception e) {
                    log.error("Error accessing PreparedStatementLogger internals: {}", e.getMessage());
                }
            }

            // Try MySQL: public asSql() method (MySQL 8.x)
            if (className.contains("mysql")) {
                try {
                    java.lang.reflect.Method asSqlMethod = stmt.getClass().getMethod("asSql");
                    String sql = (String) asSqlMethod.invoke(stmt);
                    if (sql != null) {
                        log.debug("Got SQL via MySQL public asSql() method");
                        return sql;
                    }
                } catch (NoSuchMethodException e) {
                    // Method not found, try next approach
                }

                // Try MySQL: protected asSql() method (MySQL 5.x)
                try {
                    java.lang.reflect.Method asSqlMethod = stmt.getClass().getDeclaredMethod("asSql");
                    asSqlMethod.setAccessible(true);
                    String sql = (String) asSqlMethod.invoke(stmt);
                    if (sql != null) {
                        log.debug("Got SQL via MySQL protected asSql() method");
                        return sql;
                    }
                } catch (Exception e) {
                    log.debug("Failed to access MySQL protected asSql(): {}", e.getMessage());
                }

                // Try MySQL: asSql(boolean) overload
                try {
                    java.lang.reflect.Method asSqlMethod = stmt.getClass().getMethod("asSql", boolean.class);
                    String sql = (String) asSqlMethod.invoke(stmt, true);
                    if (sql != null) {
                        log.debug("Got SQL via MySQL asSql(boolean) method");
                        return sql;
                    }
                } catch (Exception e) {
                    // Continue
                }
            }

            // Try PostgreSQL: toString() usually contains the SQL
            if (className.contains("postgresql") || className.contains("Pg")) {
                try {
                    // PostgreSQL PreparedStatement.toString() typically contains the SQL
                    String str = stmt.toString();
                    // Look for pattern like "PreparedStatement: SELECT * FROM ..."
                    if (str.contains(":")) {
                        String sql = str.substring(str.indexOf(":") + 1).trim();
                        if (!sql.isEmpty()) {
                            log.debug("Got SQL via PostgreSQL toString() method");
                            return sql;
                        }
                    }
                    // If no colon, the entire string might be the SQL
                    log.debug("Got SQL via PostgreSQL toString() method (full string)");
                    return str;
                } catch (Exception e) {
                    log.debug("Failed to extract SQL from PostgreSQL statement: {}", e.getMessage());
                }
            }

            return null;

        } catch (Exception e) {
            log.debug("Error extracting SQL from statement: {}", e.getMessage());
            return null;
        }
    }

    public static void incrementDepth() {
        ThreadLocal<Integer> depth = getCurrentDepthThreadLocal();
        depth.set(depth.get() + 1);
    }

    public static void decrementDepth() {
        ThreadLocal<Integer> depth = getCurrentDepthThreadLocal();
        int current = depth.get();
        if (current > 0) {
            depth.set(current - 1);
        }
    }

    public static int getCurrentDepth() {
        ThreadLocal<Integer> depth = getCurrentDepthThreadLocal();
        if (depth == null) {
            return 0;
        }
        Integer value = depth.get();
        return value != null ? value : 0;
    }

    public static String getRequestId() {
        // Try to get from ThreadLocal first
        ThreadLocal<String> reqIdTL = getRequestIdThreadLocal();
        String reqId = reqIdTL != null ? reqIdTL.get() : null;
        if (reqId != null && !reqId.isEmpty()) {
            log.debug("Got requestId from ThreadLocal: {}", reqId);
            return reqId;
        }

        // Try to get from RequestContextHolder via reflection
        try {
            Class<?> holderClass = Class.forName("org.springframework.web.context.request.RequestContextHolder");
            java.lang.reflect.Method getRequestAttributesMethod = holderClass.getMethod("getRequestAttributes");
            Object requestAttributes = getRequestAttributesMethod.invoke(null);

            if (requestAttributes != null) {
                java.lang.reflect.Method getRequestMethod = requestAttributes.getClass().getMethod("getRequest");
                Object request = getRequestMethod.invoke(requestAttributes);
                if (request != null) {
                    java.lang.reflect.Method getHeaderMethod = request.getClass().getMethod("getHeader", String.class);
                    // Try "requestid" (lowercase) first, then "requestId" (camelCase)
                    Object headerValue = getHeaderMethod.invoke(request, "requestid");
                    if (headerValue == null) {
                        headerValue = getHeaderMethod.invoke(request, "requestId");
                    }
                    if (headerValue == null) {
                        headerValue = getHeaderMethod.invoke(request, "RequestId");
                    }
                    if (headerValue != null) {
                        String requestId = headerValue.toString();
                        log.debug("Got requestId from RequestContextHolder header: {}", requestId);
                        return requestId;
                    }
                } else {
                    log.debug("Request is null in RequestContextHolder");
                }
            }
        } catch (Exception e) {
            log.debug("Error getting requestId from RequestContextHolder: {}", e.getMessage());
        }

        return null;
    }

    public static Map<String, String> getRequestHeaders() {
        // Try to get from ThreadLocal first
        ThreadLocal<Map<String, String>> headersTL = getRequestHeadersThreadLocal();
        Map<String, String> headers = headersTL != null ? headersTL.get() : null;
        if (headers != null && !headers.isEmpty()) {
            log.debug("Got headers from ThreadLocal: {} headers", headers.size());
            return headers;
        }

        // Try to get from RequestContextHolder via reflection
        try {
            Class<?> holderClass = Class.forName("org.springframework.web.context.request.RequestContextHolder");
            java.lang.reflect.Method getRequestAttributesMethod = holderClass.getMethod("getRequestAttributes");
            Object requestAttributes = getRequestAttributesMethod.invoke(null);

            if (requestAttributes != null) {
                java.lang.reflect.Method getRequestMethod = requestAttributes.getClass().getMethod("getRequest");
                Object request = getRequestMethod.invoke(requestAttributes);
                if (request != null) {
                    Map<String, String> extractedHeaders = extractHeadersFromRequest(request);
                    log.debug("Got headers from RequestContextHolder: {} headers", extractedHeaders.size());
                    return extractedHeaders;
                }
            }
        } catch (Exception e) {
            log.debug("Error getting headers from RequestContextHolder: {}", e.getMessage());
        }
        log.debug("No headers found from any source, returning empty map");
        return new HashMap<>();
    }

    // Extract requestId from ServletRequest (used by Agent instrumentation)
    public static String extractRequestIdFromRequest(Object request) {
        try {

            if (request == null) {
                return null;
            }

            Class<?> requestClass = request.getClass();

            java.lang.reflect.Method getHeaderMethod = requestClass.getMethod("getHeader", String.class);

            // Try both "requestid" (lowercase) and "requestId" (camelCase)
            Object headerValue = getHeaderMethod.invoke(request, "requestid");
            if (headerValue == null) {
                headerValue = getHeaderMethod.invoke(request, "requestId");
                if (headerValue != null) {
                    return headerValue.toString();
                }
            } else {
                return headerValue.toString();
            }
        } catch (Exception e) {
            log.error("Error extracting requestId: {}", e.getMessage(), e);
        }

        return null;
    }

    // Extract headers from ServletRequest (used by Agent instrumentation)
    // Note: Parameter is Object to match bytecode signature (ServletRequest is Object at runtime)
    public static Map<String, String> extractHeadersFromRequest(Object request) {
        Map<String, String> headerMap = new HashMap<>();
        try {

            if (request == null) {
                return headerMap;
            }

            Class<?> requestClass = request.getClass();

            // Directly try to call getHeaderNames and getHeader methods on the request object
            // This works even if we can't load HttpServletRequest interface class
            try {
                java.lang.reflect.Method getHeaderNamesMethod = requestClass.getMethod("getHeaderNames");
                java.lang.reflect.Method getHeaderMethod = requestClass.getMethod("getHeader", String.class);

                Object headerNames = getHeaderNamesMethod.invoke(request);
                if (headerNames instanceof java.util.Enumeration) {
                    @SuppressWarnings("unchecked")
                    java.util.Enumeration<String> enumNames = (java.util.Enumeration<String>) headerNames;
                    while (enumNames.hasMoreElements()) {
                        String headerName = enumNames.nextElement();
                        Object headerValue = getHeaderMethod.invoke(request, headerName);
                        if (headerValue != null) {
                            headerMap.put(headerName, headerValue.toString());
                        }
                    }
                }else{
                    log.error("getHeaderNames did not return Enumeration, got: {}", headerNames.getClass().getName());
                }
            } catch (Exception e) {
                log.error("Error extracting headers: {}", e.getMessage());
            }
        } catch (Exception e) {
            log.error("Error extracting headers: {}", e.getMessage(), e);
        }

        return headerMap;
    }

    private static void sendBatch(List<BatchData> batch) {
        if (batch.isEmpty()) {
            return;
        }

        try {
            // Build JSON payload
            StringBuilder json = new StringBuilder("[");
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

            for (int i = 0; i < batch.size(); i++) {
                BatchData data = batch.get(i);
                if (i > 0) {
                    json.append(",");
                }

                json.append("{");
                json.append("\"requestId\":\"").append(escapeJson(data.requestId)).append("\",");
                json.append("\"startTime\":\"").append(data.startTime.format(formatter)).append("\",");
                json.append("\"endTime\":\"").append(data.endTime.format(formatter)).append("\",");

                // Headers
                json.append("\"headers\":{");
                boolean firstHeader = true;
                for (Map.Entry<String, String> entry : data.headers.entrySet()) {
                    if (!firstHeader) {
                        json.append(",");
                    }
                    json.append("\"").append(escapeJson(entry.getKey())).append("\":\"");
                    json.append(escapeJson(entry.getValue())).append("\"");
                    firstHeader = false;
                }
                json.append("},");

                // Method calls
                json.append("\"methodCalls\":[");
                for (int j = 0; j < data.methodCalls.size(); j++) {
                    MethodCallInfo call = data.methodCalls.get(j);
                    if (j > 0) {
                        json.append(",");
                    }
                    json.append("{");
                    json.append("\"className\":\"").append(escapeJson(call.className)).append("\",");
                    json.append("\"methodName\":\"").append(escapeJson(call.methodName)).append("\",");
                    json.append("\"packageName\":\"").append(escapeJson(call.packageName)).append("\",");
                    json.append("\"depth\":").append(call.depth).append(",");
                    json.append("\"sequence\":").append(call.sequence).append(",");
                    json.append("\"callTime\":\"").append(call.callTime.format(formatter)).append("\",");
                    json.append("\"parentIndex\":").append(call.parentIndex);
                    if (call.query != null && !call.query.isEmpty()) {
                        json.append(",\"query\":\"").append(escapeJson(call.query)).append("\"");
                    }
                    json.append("}");
                }
                json.append("]");
                json.append("}");
            }

            json.append("]");

            // Send HTTP POST request
            String apiUrl = getApiUrl();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = getHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
            } else {
                log.error("Failed to send tracking data: HTTP {}, Response: {}", response.statusCode(), response.body());
            }

        } catch (Exception e) {
            log.error("Error sending tracking data: {}", e.getMessage(), e);
        }
    }

    private static String escapeJson(String str) {
        if (str == null) {
            return "";
        }
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}



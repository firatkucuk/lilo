package io.fria.lilo;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.DataFetcherExceptionHandler;
import graphql.execution.instrumentation.Instrumentation;
import io.fria.lilo.error.SourceDataFetcherExceptionHandler;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Lilo {

  private final LiloContext context;

  private Lilo(final @NotNull LiloContext context) {
    this.context = context;
  }

  public static @NotNull LiloBuilder builder() {
    return new LiloBuilder();
  }

  public @NotNull LiloContext getContext() {
    return this.context;
  }

  public @NotNull ExecutionResult stitch(final @NotNull ExecutionInput executionInput) {

    if (IntrospectionFetchingMode.FETCH_BEFORE_EVERY_REQUEST
        == this.context.getIntrospectionFetchingMode()) {
      this.context.invalidateAll();
    }

    return this.context.getGraphQL(executionInput).execute(executionInput);
  }

  public @NotNull CompletableFuture<ExecutionResult> stitchAsync(
      final @NotNull ExecutionInput executionInput) {

    if (IntrospectionFetchingMode.FETCH_BEFORE_EVERY_REQUEST
        == this.context.getIntrospectionFetchingMode()) {
      this.context.invalidateAll();
    }

    return this.context
        .getGraphQLAsync(executionInput)
        .thenCompose(graphQL -> graphQL.executeAsync(executionInput));
  }

  public static final class LiloBuilder {

    private final Map<String, SchemaSource> schemaSources = new HashMap<>();
    private DataFetcherExceptionHandler dataFetcherExceptionHandler =
        new SourceDataFetcherExceptionHandler();
    private IntrospectionFetchingMode introspectionFetchingMode =
        IntrospectionFetchingMode.CACHE_UNTIL_INVALIDATION;
    private boolean retrySchemaLoad = true;
    private Instrumentation instrumentation;

    @SuppressWarnings("checkstyle:WhitespaceAround")
    private LiloBuilder() {}

    public @NotNull LiloBuilder addSource(final @NotNull SchemaSource schemaSource) {
      this.schemaSources.put(schemaSource.getName(), Objects.requireNonNull(schemaSource));
      return this;
    }

    public @NotNull Lilo build() {

      return new Lilo(
          new LiloContext(
              this.dataFetcherExceptionHandler,
              this.introspectionFetchingMode,
              this.retrySchemaLoad,
              this.instrumentation,
              this.schemaSources.values().toArray(new SchemaSource[0])));
    }

    public @NotNull LiloBuilder defaultDataFetcherExceptionHandler(
        final @NotNull DataFetcherExceptionHandler defaultDataFetcherExceptionHandler) {
      this.dataFetcherExceptionHandler = defaultDataFetcherExceptionHandler;
      return this;
    }

    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder instrumentation(final @Nullable Instrumentation instrumentation) {
      this.instrumentation = instrumentation;
      return this;
    }

    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder introspectionFetchingMode(
        final @NotNull IntrospectionFetchingMode introspectionFetchingMode) {
      this.introspectionFetchingMode = introspectionFetchingMode;
      return this;
    }

    @SuppressWarnings("HiddenField")
    public @NotNull LiloBuilder retrySchemaLoad(final boolean retrySchemaLoad) {
      this.retrySchemaLoad = retrySchemaLoad;
      return this;
    }
  }
}

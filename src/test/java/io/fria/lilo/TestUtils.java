package io.fria.lilo;

import graphql.GraphQL;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

public final class TestUtils {

  private TestUtils() {
    // Utility class
  }

  public static @NotNull GraphQL createGraphQL(
      final String schemaDefinitionPath, final RuntimeWiring runtimeWiring) {

    final var schemaDefinitionText = loadResource(schemaDefinitionPath);
    final var typeRegistry = new SchemaParser().parse(schemaDefinitionText);
    final var graphQLSchema =
        new SchemaGenerator().makeExecutableSchema(typeRegistry, runtimeWiring);

    return GraphQL.newGraphQL(graphQLSchema).build();
  }

  public static @NotNull String loadResource(final @NotNull String path) {

    try {
      final InputStream stream = TestUtils.class.getResourceAsStream(path);

      if (stream != null) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
      }
    } catch (final IOException e) {
      // pass to the exception
    }

    throw new IllegalArgumentException(String.format("Resource %s not found", path));
  }

  private static @NotNull String runQuery(
      final @NotNull GraphQL graphQL, final @NotNull String query) {

    final var graphQLRequestOptional = toObj(query, GraphQLRequest.class);

    if (graphQLRequestOptional.isEmpty()) {
      throw new IllegalArgumentException("Query request is invalid: Empty query");
    }

    return runQuery(graphQL, graphQLRequestOptional.get());
  }

  private static @NotNull String runQuery(
      final @NotNull GraphQL graphQL, final @NotNull GraphQLRequest graphQLRequest) {
    return toStr(graphQL.execute(graphQLRequest.toExecutionInput()));
  }

  public static class TestIntrospectionRetriever implements IntrospectionRetriever {

    private GraphQL graphQL;

    public TestIntrospectionRetriever(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }

    @Override
    public @NotNull String get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull String query,
        final @Nullable Object localContext) {
      return runQuery(this.graphQL, query);
    }

    public void setGraphQL(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }

  public static class TestQueryRetriever implements QueryRetriever {

    private GraphQL graphQL;

    public TestQueryRetriever(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }

    @Override
    public @NotNull String get(
        final @NotNull LiloContext liloContext,
        final @NotNull SchemaSource schemaSource,
        final @NotNull GraphQLQuery query,
        final @Nullable Object localContext) {
      return runQuery(this.graphQL, query.getQuery());
    }

    public void setGraphQL(final @NotNull GraphQL graphQL) {
      this.graphQL = graphQL;
    }
  }
}

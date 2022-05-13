package io.fria.lilo;

import graphql.ExecutionResult;
import graphql.ExecutionResultImpl;
import graphql.GraphQLError;
import graphql.introspection.IntrospectionResultToSchema;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import io.fria.lilo.error.LiloGraphQLError;
import io.fria.lilo.error.SourceDataFetcherException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static graphql.schema.idl.TypeRuntimeWiring.newTypeWiring;
import static io.fria.lilo.JsonUtils.getMap;
import static io.fria.lilo.JsonUtils.toMap;
import static io.fria.lilo.JsonUtils.toObj;
import static io.fria.lilo.JsonUtils.toStr;

public final class RemoteSchemaSource implements SchemaSource {

  private static final Logger LOG = LoggerFactory.getLogger(RemoteSchemaSource.class);
  private static final String INTROSPECTION_REQUEST =
      toStr(
          GraphQLRequest.builder()
              .query(GraphQLRequest.INTROSPECTION_QUERY)
              .operationName("IntrospectionQuery")
              .build());

  private final String schemaName;
  private final IntrospectionRetriever introspectionRetriever;
  private final QueryRetriever queryRetriever;
  private TypeDefinitionRegistry typeDefinitionRegistry;
  private RuntimeWiring runtimeWiring;

  private RemoteSchemaSource(
      final @NotNull String schemaName,
      final @NotNull IntrospectionRetriever introspectionRetriever,
      final @NotNull QueryRetriever queryRetriever) {
    this.schemaName = schemaName;
    this.introspectionRetriever = introspectionRetriever;
    this.queryRetriever = queryRetriever;
  }

  public static @NotNull SchemaSource create(
      final @NotNull String schemaName,
      final @NotNull IntrospectionRetriever introspectionRetriever,
      final @NotNull QueryRetriever queryRetriever) {

    return new RemoteSchemaSource(
        Objects.requireNonNull(schemaName),
        Objects.requireNonNull(introspectionRetriever),
        Objects.requireNonNull(queryRetriever));
  }

  @Override
  public @NotNull ExecutionResult execute(
      final @NotNull LiloContext liloContext,
      final @NotNull SchemaSource schemaSource,
      final @NotNull GraphQLQuery query,
      final @Nullable Object localContext) {

    final var queryResult = this.queryRetriever.get(liloContext, schemaSource, query, localContext);
    final var graphQLResultOptional = toObj(queryResult, GraphQLResult.class);

    if (graphQLResultOptional.isEmpty()) {
      throw new IllegalArgumentException("DataFetcher caught an empty response");
    }

    return graphQLResultOptional.get();
  }

  @Override
  public @NotNull String getName() {
    return this.schemaName;
  }

  @Override
  public @NotNull RuntimeWiring getRuntimeWiring() {

    if (!this.isSchemaLoaded()) {
      throw new IllegalArgumentException(this.schemaName + " has not been loaded yet!");
    }

    return this.runtimeWiring;
  }

  @Override
  public @NotNull TypeDefinitionRegistry getTypeDefinitionRegistry() {

    if (!this.isSchemaLoaded()) {
      throw new IllegalArgumentException(this.schemaName + " has not been loaded yet!");
    }

    return this.typeDefinitionRegistry;
  }

  @Override
  public void invalidate() {
    this.typeDefinitionRegistry = null;
  }

  @Override
  public boolean isSchemaLoaded() {
    return this.typeDefinitionRegistry != null;
  }

  @Override
  public void loadSchema(
      final @NotNull LiloContext liloContext, final @Nullable Object localContext) {

    final String introspectionResponse;

    try {
      introspectionResponse =
          this.introspectionRetriever.get(liloContext, this, INTROSPECTION_REQUEST, localContext);
    } catch (final Exception e) {
      LOG.error("Could not load introspection for {}", this.schemaName);
      LOG.debug("Introspection fetching exception", e);
      return;
    }

    final var introspectionResultOptional = toMap(introspectionResponse);

    if (introspectionResultOptional.isEmpty()) {
      throw new IllegalArgumentException("Introspection response is empty");
    }

    final var dataOptional = getMap(introspectionResultOptional.get(), "data");

    if (dataOptional.isEmpty()) {
      throw new IllegalArgumentException(
          "Introspection response is not valid, requires data section");
    }

    final var schemaDoc =
        new IntrospectionResultToSchema().createSchemaDefinition(dataOptional.get());
    final var typeDefinitionRegistry = new SchemaParser().buildRegistry(schemaDoc);
    final var operationTypeNames = SchemaMerger.getOperationTypeNames(typeDefinitionRegistry);

    final RuntimeWiring.Builder runtimeWiringBuilder = RuntimeWiring.newRuntimeWiring();
    this.typeWiring(typeDefinitionRegistry, liloContext, operationTypeNames.getQuery())
        .ifPresent(runtimeWiringBuilder::type);
    this.typeWiring(typeDefinitionRegistry, liloContext, operationTypeNames.getMutation())
        .ifPresent(runtimeWiringBuilder::type);

    this.runtimeWiring = runtimeWiringBuilder.build();
    this.typeDefinitionRegistry = typeDefinitionRegistry;
  }

  private @Nullable Object fetchData(
      final @NotNull DataFetchingEnvironment environment, final @NotNull LiloContext liloContext) {

    final var query = QueryTransformer.extractQuery(environment);
    final var graphQLResult = this.execute(liloContext, this, query, environment.getLocalContext());
    final List<GraphQLError> errors = graphQLResult.getErrors();

    if (errors != null && !errors.isEmpty()) {
      throw new SourceDataFetcherException(errors);
    }

    return ((Map<String, Object>) graphQLResult.getData()).values().iterator().next();
  }

  private @NotNull Optional<TypeRuntimeWiring> typeWiring(
      final @NotNull TypeDefinitionRegistry typeDefinitionRegistry,
      final @NotNull LiloContext liloContext,
      final @Nullable String typeName) {

    if (typeName == null) {
      return Optional.empty();
    }

    final var typeWiringBuilder = newTypeWiring(typeName);
    final var typeDefinitionOptional = typeDefinitionRegistry.getType(typeName);

    if (typeDefinitionOptional.isEmpty()) {
      return Optional.empty();
    }

    final ObjectTypeDefinition typeDefinition = (ObjectTypeDefinition) typeDefinitionOptional.get();
    final List<FieldDefinition> fields = typeDefinition.getFieldDefinitions();

    for (final FieldDefinition field : fields) {
      typeWiringBuilder.dataFetcher(field.getName(), e -> this.fetchData(e, liloContext));
    }

    return Optional.of(typeWiringBuilder.build());
  }

  private static final class GraphQLResult implements ExecutionResult {

    private Map<String, Object> data;
    private List<LiloGraphQLError> errors;
    private Map<Object, Object> extensions;

    @Override
    public @NotNull Map<String, Object> getData() {
      return this.data;
    }

    @Override
    public @Nullable List<GraphQLError> getErrors() {

      if (this.errors == null) {
        return null;
      }

      return this.errors.stream().map(le -> (GraphQLError) le).collect(Collectors.toList());
    }

    @Override
    public @Nullable Map<Object, Object> getExtensions() {
      return this.extensions;
    }

    @Override
    public boolean isDataPresent() {
      return this.data != null;
    }

    @Override
    public Map<String, Object> toSpecification() {

      return ExecutionResultImpl.newExecutionResult()
          .data(this.data)
          .errors(this.getErrors())
          .extensions(this.extensions)
          .build()
          .toSpecification();
    }
  }
}

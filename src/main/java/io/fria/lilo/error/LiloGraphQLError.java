package io.fria.lilo.error;

import graphql.ErrorClassification;
import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LiloGraphQLError implements GraphQLError {

  private String message;
  private List<ErrorSourceLocation> locations;
  private ErrorType errorType;
  private List<Object> path;
  private Map<String, Object> extensions;

  @SuppressWarnings("checkstyle:WhitespaceAround")
  public LiloGraphQLError() {}

  @Override
  public @Nullable ErrorClassification getErrorType() {
    return this.errorType;
  }

  public void setErrorType(final @Nullable ErrorType errorType) {
    this.errorType = errorType;
  }

  @Override
  public @Nullable Map<String, Object> getExtensions() {
    return this.extensions;
  }

  public void setExtensions(final @Nullable Map<String, Object> extensions) {
    this.extensions = extensions;
  }

  @Override
  public @Nullable List<SourceLocation> getLocations() {

    if (this.locations == null) {
      return null;
    }

    return this.locations.stream()
        .map(l -> new ErrorSourceLocation(l.getLine(), l.getColumn(), l.getSourceName()))
        .collect(Collectors.toList());
  }

  public void setLocations(final @Nullable List<ErrorSourceLocation> locations) {
    this.locations = locations;
  }

  @Override
  public @NotNull String getMessage() {
    return this.message;
  }

  public void setMessage(final @NotNull String message) {
    this.message = Objects.requireNonNull(message);
  }

  @Override
  public @Nullable List<Object> getPath() {
    return this.path;
  }

  public void setPath(final @Nullable List<Object> path) {
    this.path = path;
  }
}

package com.amazon.tdm.s3a.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>This exception is thrown when the requested entity is not found in the server side.</p>
 */
public class EntityNotFoundException extends RuntimeException  {

  /**
   * Statically creates a builder instance for EntityNotFoundException.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of EntityNotFoundException.
   */
  public static class Builder {

    protected String message;
    /**
     * Sets the value of the field "message" to be used for the constructed object.
     * @param message
     *   The value of the "message" field.
     * @return
     *   This builder.
     */
    public Builder withMessage(String message) {
      this.message = message;
      return this;
    }

    private Throwable cause;
    /**
     * Sets the cause of this exception.
     * @param cause
     *   The exception cause.
     * @return
     *   This builder.
     */
    public Builder withCause(Throwable cause) {
      this.cause = cause;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(EntityNotFoundException instance) {
    }

    /**
     * Builds an instance of EntityNotFoundException.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public EntityNotFoundException build() {
      EntityNotFoundException instance = new EntityNotFoundException(message, cause);

      populate(instance);

      return instance;
    }
  };

  private static final long serialVersionUID = -1L;

  public EntityNotFoundException() {
  }

  public EntityNotFoundException(Throwable cause) {
    initCause(cause);
  }

  public EntityNotFoundException(String message) {
    super(message);
  }

  public EntityNotFoundException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }

  @Override
  public String getMessage() { 
    return super.getMessage();
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.tdm.s3a.model.EntityNotFoundException");

  /**
   * HashCode implementation for EntityNotFoundException
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getMessage());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for EntityNotFoundException
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof EntityNotFoundException)) {
      return false;
    }

    EntityNotFoundException that = (EntityNotFoundException) other;

    return
        Objects.equals(getMessage(), that.getMessage());
  }

}

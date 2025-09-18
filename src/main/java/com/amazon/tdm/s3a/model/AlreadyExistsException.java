package com.amazon.tdm.s3a.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>This exception is thrown when creating a resource that already exists.</p>
 */
public class AlreadyExistsException extends RuntimeException  {

  /**
   * Statically creates a builder instance for AlreadyExistsException.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of AlreadyExistsException.
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
    protected void populate(AlreadyExistsException instance) {
    }

    /**
     * Builds an instance of AlreadyExistsException.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public AlreadyExistsException build() {
      AlreadyExistsException instance = new AlreadyExistsException(message, cause);

      populate(instance);

      return instance;
    }
  };

  private static final long serialVersionUID = -1L;

  public AlreadyExistsException() {
  }

  public AlreadyExistsException(Throwable cause) {
    initCause(cause);
  }

  public AlreadyExistsException(String message) {
    super(message);
  }

  public AlreadyExistsException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }

  @Override
  public String getMessage() { 
    return super.getMessage();
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.tdm.s3a.AlreadyExistsException");

  /**
   * HashCode implementation for AlreadyExistsException
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
   * Equals implementation for AlreadyExistsException
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof AlreadyExistsException)) {
      return false;
    }

    AlreadyExistsException that = (AlreadyExistsException) other;

    return
        Objects.equals(getMessage(), that.getMessage());
  }

}

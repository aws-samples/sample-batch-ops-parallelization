package com.amazon.bopspar.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>This exception is thrown when AWS SDK throws any exceptions.</p>
 */
public class AWSServiceException extends RuntimeException  {

  /**
   * Statically creates a builder instance for AWSServiceException.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of AWSServiceException.
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
    protected void populate(AWSServiceException instance) {
    }

    /**
     * Builds an instance of AWSServiceException.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public AWSServiceException build() {
      AWSServiceException instance = new AWSServiceException(message, cause);

      populate(instance);

      return instance;
    }
  };

  private static final long serialVersionUID = -1L;

  public AWSServiceException() {
  }

  public AWSServiceException(Throwable cause) {
    initCause(cause);
  }

  public AWSServiceException(String message) {
    super(message);
  }

  public AWSServiceException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }

  @Override
  public String getMessage() { 
    return super.getMessage();
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.bopspar.AWSServiceException");

  /**
   * HashCode implementation for AWSServiceException
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
   * Equals implementation for AWSServiceException
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof AWSServiceException)) {
      return false;
    }

    AWSServiceException that = (AWSServiceException) other;

    return
        Objects.equals(getMessage(), that.getMessage());
  }

}

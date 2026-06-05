package com.amazon.bopspar.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>This exception is thrown when the format of the input is incorrect.</p>
 */
public class InvalidInputException extends RuntimeException  {

  /**
   * Statically creates a builder instance for InvalidInputException.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of InvalidInputException.
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
    protected void populate(InvalidInputException instance) {
    }

    /**
     * Builds an instance of InvalidInputException.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public InvalidInputException build() {
      InvalidInputException instance = new InvalidInputException(message, cause);

      populate(instance);

      return instance;
    }
  };

  private static final long serialVersionUID = -1L;

  public InvalidInputException() {
  }

  public InvalidInputException(Throwable cause) {
    initCause(cause);
  }

  public InvalidInputException(String message) {
    super(message);
  }

  public InvalidInputException(String message, Throwable cause) {
    super(message);
    initCause(cause);
  }

  @Override
  public String getMessage() { 
    return super.getMessage();
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.bopspar.model.InvalidInputException");

  /**
   * HashCode implementation for InvalidInputException
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
   * Equals implementation for InvalidInputException
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof InvalidInputException)) {
      return false;
    }

    InvalidInputException that = (InvalidInputException) other;

    return
        Objects.equals(getMessage(), that.getMessage());
  }

}

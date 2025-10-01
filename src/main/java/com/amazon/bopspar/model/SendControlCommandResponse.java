package com.amazon.bopspar.model;

import java.util.Arrays;
import java.util.Objects;

public class SendControlCommandResponse extends Object  {

  /**
   * Statically creates a builder instance for SendControlCommandResponse.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of SendControlCommandResponse.
   */
  public static class Builder {

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(SendControlCommandResponse instance) {
    }

    /**
     * Builds an instance of SendControlCommandResponse.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public SendControlCommandResponse build() {
      SendControlCommandResponse instance = new SendControlCommandResponse();

      populate(instance);

      return instance;
    }
  };

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.bopspar.SendControlCommandResponse");

  /**
   * HashCode implementation for SendControlCommandResponse
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode);
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for SendControlCommandResponse
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof SendControlCommandResponse)) {
      return false;
    }

    return true;
  }

  /**
   * Returns a string representation of this object. The content of any types marked with the
   * <a href="https://w.amazon.com/index.php/Coral/Model/XML/Traits#Sensitive">sensitive</a>
   * trait will be redacted.
   * <p/>
   * <b>Do not attempt to parse the string returned by this method.</b> Coral's <tt>toString</tt>
   * format is undefined and subject to change. To obtain a proper machine-readable representation
   * of this object, use Coral Serialize directly.
   * @see <a href="https://w.amazon.com/index.php/Coral/Serialize/Manual">Coral Serialize Manual</a>
   * @see <a href="https://w.amazon.com/index.php/Coral/Serialize/FAQ">Coral Serialize FAQ</a>
   */
  @Override
  public String toString() {
    StringBuilder ret = new StringBuilder();
    ret.append("SendControlCommandResponse(");

    ret.append(")");

    return ret.toString();
  }

}

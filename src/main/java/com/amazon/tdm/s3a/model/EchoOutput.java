package com.amazon.tdm.s3a.model;

import java.util.Arrays;
import java.util.Objects;

public class EchoOutput extends Object  {

  /**
   * Statically creates a builder instance for EchoOutput.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of EchoOutput.
   */
  public static class Builder {

    protected String string;
    /**
     * Sets the value of the field "string" to be used for the constructed object.
     * @param string
     *   The value of the "string" field.
     * @return
     *   This builder.
     */
    public Builder withString(String string) {
      this.string = string;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(EchoOutput instance) {
      instance.setString(this.string);
    }

    /**
     * Builds an instance of EchoOutput.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public EchoOutput build() {
      EchoOutput instance = new EchoOutput();

      populate(instance);

      return instance;
    }
  };

  private String string;

  public String getString() {
    return this.string;
  }

  public void setString(String string) {
    this.string = string;
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.tdm.s3a.EchoOutput");

  /**
   * HashCode implementation for EchoOutput
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getString());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for EchoOutput
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof EchoOutput)) {
      return false;
    }

    EchoOutput that = (EchoOutput) other;

    return
        Objects.equals(getString(), that.getString());
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
    ret.append("EchoOutput(");

    ret.append("string=");
    ret.append(String.valueOf(string));
    ret.append(")");

    return ret.toString();
  }

}

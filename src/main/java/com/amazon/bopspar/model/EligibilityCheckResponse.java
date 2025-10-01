package com.amazon.bopspar.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>Eligibility check response</p>
 */
public class EligibilityCheckResponse extends Object  {

  /**
   * Statically creates a builder instance for EligibilityCheckResponse.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of EligibilityCheckResponse.
   */
  public static class Builder {

    protected String eligibilityCheckResponse;
    /**
     * Sets the value of the field "eligibilityCheckResponse" to be used for the constructed object.
     * @param eligibilityCheckResponse
     *   The value of the "eligibilityCheckResponse" field.
     * @return
     *   This builder.
     */
    public Builder withEligibilityCheckResponse(String eligibilityCheckResponse) {
      this.eligibilityCheckResponse = eligibilityCheckResponse;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(EligibilityCheckResponse instance) {
      instance.setEligibilityCheckResponse(this.eligibilityCheckResponse);
    }

    /**
     * Builds an instance of EligibilityCheckResponse.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public EligibilityCheckResponse build() {
      EligibilityCheckResponse instance = new EligibilityCheckResponse();

      populate(instance);

      return instance;
    }
  };

  private String eligibilityCheckResponse;

  public String getEligibilityCheckResponse() {
    return this.eligibilityCheckResponse;
  }

  public void setEligibilityCheckResponse(String eligibilityCheckResponse) {
    this.eligibilityCheckResponse = eligibilityCheckResponse;
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.bopspar.EligibilityCheckResponse");

  /**
   * HashCode implementation for EligibilityCheckResponse
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getEligibilityCheckResponse());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for EligibilityCheckResponse
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof EligibilityCheckResponse)) {
      return false;
    }

    EligibilityCheckResponse that = (EligibilityCheckResponse) other;

    return
        Objects.equals(getEligibilityCheckResponse(), that.getEligibilityCheckResponse());
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
    ret.append("EligibilityCheckResponse(");

    ret.append("eligibilityCheckResponse=");
    ret.append(String.valueOf(eligibilityCheckResponse));
    ret.append(")");

    return ret.toString();
  }

}

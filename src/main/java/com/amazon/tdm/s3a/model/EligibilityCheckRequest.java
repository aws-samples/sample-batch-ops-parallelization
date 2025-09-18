package com.amazon.tdm.s3a.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>Input list of bucket names for eligibility check</p>
 */
public class EligibilityCheckRequest extends Object  {

  /**
   * Statically creates a builder instance for EligibilityCheckRequest.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of EligibilityCheckRequest.
   */
  public static class Builder {

    protected Workflow eligibilityCheckRequest;
    /**
     * Sets the value of the field "eligibilityCheckRequest" to be used for the constructed object.
     * @param eligibilityCheckRequest
     *   The value of the "eligibilityCheckRequest" field.
     * @return
     *   This builder.
     */
    public Builder withEligibilityCheckRequest(Workflow eligibilityCheckRequest) {
      this.eligibilityCheckRequest = eligibilityCheckRequest;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(EligibilityCheckRequest instance) {
      instance.setEligibilityCheckRequest(this.eligibilityCheckRequest);
    }

    /**
     * Builds an instance of EligibilityCheckRequest.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public EligibilityCheckRequest build() {
      EligibilityCheckRequest instance = new EligibilityCheckRequest();

      populate(instance);

      return instance;
    }
  };

  private Workflow eligibilityCheckRequest;

  /**
   * @return <p>Pass a workflow object as input. This maintains consistency across all other APIs.</p>
   */
  public Workflow getEligibilityCheckRequest() {
    return this.eligibilityCheckRequest;
  }

  public void setEligibilityCheckRequest(Workflow eligibilityCheckRequest) {
    this.eligibilityCheckRequest = eligibilityCheckRequest;
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.tdm.s3a.EligibilityCheckRequest");

  /**
   * HashCode implementation for EligibilityCheckRequest
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getEligibilityCheckRequest());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for EligibilityCheckRequest
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof EligibilityCheckRequest)) {
      return false;
    }

    EligibilityCheckRequest that = (EligibilityCheckRequest) other;

    return
        Objects.equals(getEligibilityCheckRequest(), that.getEligibilityCheckRequest());
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
    ret.append("EligibilityCheckRequest(");

    ret.append("eligibilityCheckRequest=");
    ret.append(String.valueOf(eligibilityCheckRequest));
    ret.append(")");

    return ret.toString();
  }

}

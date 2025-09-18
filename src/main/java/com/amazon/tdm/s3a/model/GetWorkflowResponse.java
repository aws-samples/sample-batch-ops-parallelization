package com.amazon.tdm.s3a.model;

import java.util.Arrays;
import java.util.Objects;

public class GetWorkflowResponse extends Object  {

  /**
   * Statically creates a builder instance for GetWorkflowResponse.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of GetWorkflowResponse.
   */
  public static class Builder {

    protected Workflow workflow;
    /**
     * Sets the value of the field "workflow" to be used for the constructed object.
     * @param workflow
     *   The value of the "workflow" field.
     * @return
     *   This builder.
     */
    public Builder withWorkflow(Workflow workflow) {
      this.workflow = workflow;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(GetWorkflowResponse instance) {
      instance.setWorkflow(this.workflow);
    }

    /**
     * Builds an instance of GetWorkflowResponse.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public GetWorkflowResponse build() {
      GetWorkflowResponse instance = new GetWorkflowResponse();

      populate(instance);

      return instance;
    }
  };

  private Workflow workflow;

  /**
   * @return <p>Workflow details</p>
   */
  public Workflow getWorkflow() {
    return this.workflow;
  }

  public void setWorkflow(Workflow workflow) {
    this.workflow = workflow;
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.tdm.s3a.GetWorkflowResponse");

  /**
   * HashCode implementation for GetWorkflowResponse
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getWorkflow());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for GetWorkflowResponse
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof GetWorkflowResponse)) {
      return false;
    }

    GetWorkflowResponse that = (GetWorkflowResponse) other;

    return
        Objects.equals(getWorkflow(), that.getWorkflow());
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
    ret.append("GetWorkflowResponse(");

    ret.append("workflow=");
    ret.append(String.valueOf(workflow));
    ret.append(")");

    return ret.toString();
  }

}

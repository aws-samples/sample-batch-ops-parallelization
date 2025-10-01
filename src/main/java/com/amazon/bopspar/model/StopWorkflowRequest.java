package com.amazon.bopspar.model;

import java.util.Arrays;
import java.util.Objects;

public class StopWorkflowRequest extends Object  {

  /**
   * Statically creates a builder instance for StopWorkflowRequest.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of StopWorkflowRequest.
   */
  public static class Builder {

    protected String namespaceID;
    /**
     * Sets the value of the field "namespaceID" to be used for the constructed object.
     * @param namespaceID
     *   The value of the "namespaceID" field.
     * @return
     *   This builder.
     */
    public Builder withNamespaceID(String namespaceID) {
      this.namespaceID = namespaceID;
      return this;
    }

    protected String workflowName;
    /**
     * Sets the value of the field "workflowName" to be used for the constructed object.
     * @param workflowName
     *   The value of the "workflowName" field.
     * @return
     *   This builder.
     */
    public Builder withWorkflowName(String workflowName) {
      this.workflowName = workflowName;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(StopWorkflowRequest instance) {
      instance.setNamespaceID(this.namespaceID);
      instance.setWorkflowName(this.workflowName);
    }

    /**
     * Builds an instance of StopWorkflowRequest.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public StopWorkflowRequest build() {
      StopWorkflowRequest instance = new StopWorkflowRequest();

      populate(instance);

      return instance;
    }
  };

  private String namespaceID;
  private String workflowName;

  /**
   * @return <p>Namespace ID</p>
   */
  public String getNamespaceID() {
    return this.namespaceID;
  }

  public void setNamespaceID(String namespaceID) {
    this.namespaceID = namespaceID;
  }

  /**
   * @return <p>Name of the Workflow</p>
   */
  public String getWorkflowName() {
    return this.workflowName;
  }

  public void setWorkflowName(String workflowName) {
    this.workflowName = workflowName;
  }

  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.bopspar.StopWorkflowRequest");

  /**
   * HashCode implementation for StopWorkflowRequest
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getNamespaceID(),
        getWorkflowName());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for StopWorkflowRequest
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof StopWorkflowRequest)) {
      return false;
    }

    StopWorkflowRequest that = (StopWorkflowRequest) other;

    return
        Objects.equals(getNamespaceID(), that.getNamespaceID())
        && Objects.equals(getWorkflowName(), that.getWorkflowName());
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
    ret.append("StopWorkflowRequest(");

    ret.append("namespaceID=");
    ret.append(String.valueOf(namespaceID));
    ret.append(", ");

    ret.append("workflowName=");
    ret.append(String.valueOf(workflowName));
    ret.append(")");

    return ret.toString();
  }

}

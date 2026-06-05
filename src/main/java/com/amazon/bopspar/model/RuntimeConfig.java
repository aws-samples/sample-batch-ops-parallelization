package com.amazon.bopspar.model;

import java.util.Arrays;
import java.util.Objects;

/**
 * <p>Structure to hold runtime configuration</p>
 */
public class RuntimeConfig extends Object  {

  /**
   * Statically creates a builder instance for RuntimeConfig.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Fluent builder for instances of RuntimeConfig.
   */
  public static class Builder {

    protected Boolean isReplicationTimeControlEnabled;

    public Builder withReplicationTimeControlEnabled(Boolean isReplicationTimeControlEnabled) {
      this.isReplicationTimeControlEnabled = isReplicationTimeControlEnabled;
      return this;
    }

    protected String dashboardUrl;
    /**
     * Sets the value of the field "dashboardUrl" to be used for the constructed object.
     * @param dashboardUrl
     *   The value of the "dashboardUrl" field.
     * @return
     *   This builder.
     */
    public Builder withDashboardUrl(String dashboardUrl) {
      this.dashboardUrl = dashboardUrl;
      return this;
    }

    protected String manifestLocation;
    /**
     * Sets the value of the field "manifestLocation" to be used for the constructed object.
     * @param manifestLocation
     *   The value of the "manifestLocation" field.
     * @return
     *   This builder.
     */
    public Builder withManifestLocation(String manifestLocation) {
      this.manifestLocation = manifestLocation;
      return this;
    }

    protected Boolean skipBucketOwnershipValidationAndCopy;
    /**
     * Sets the value of the field "skipBucketOwnershipValidationAndCopy" to be used for the constructed object.
     * @param skipBucketOwnershipValidationAndCopy
     *   The value of the "skipBucketOwnershipValidationAndCopy" field.
     * @return
     *   This builder.
     */
    public Builder withSkipBucketOwnershipValidationAndCopy(Boolean skipBucketOwnershipValidationAndCopy) {
      this.skipBucketOwnershipValidationAndCopy = skipBucketOwnershipValidationAndCopy;
      return this;
    }

    /**
     * Sets the fields of the given instances to the corresponding values recorded when calling the "with*" methods.
     * @param instance
     *   The instance to be populated.
     */
    protected void populate(RuntimeConfig instance) {
      instance.setDashboardUrl(this.dashboardUrl);
      instance.setManifestLocation(this.manifestLocation);
      instance.setSkipBucketOwnershipValidationAndCopy(this.skipBucketOwnershipValidationAndCopy);
      instance.setReplicationTimeControlEnabled(this.isReplicationTimeControlEnabled);
    }

    /**
     * Builds an instance of RuntimeConfig.
     * <p>
     * The built object has its fields set to the values given when calling the "with*" methods of this builder.
     * </p>
     */
    public RuntimeConfig build() {
      RuntimeConfig instance = new RuntimeConfig();

      populate(instance);

      return instance;
    }
  };

  private String dashboardUrl;
  private String manifestLocation;
  private Boolean skipBucketOwnershipValidationAndCopy;
  private Boolean isReplicationTimeControlEnabled;


  /**
   * @return <p>CloudWatch Dashboard Url</p>
   */
  public String getDashboardUrl() {
    return this.dashboardUrl;
  }

  public void setDashboardUrl(String dashboardUrl) {
    this.dashboardUrl = dashboardUrl;
  }

  /**
   * @return <p>S3 URI for manifest location</p>
   */
  public String getManifestLocation() {
    return this.manifestLocation;
  }

  public void setManifestLocation(String manifestLocation) {
    this.manifestLocation = manifestLocation;
  }

  /**
   * @return <p>Flag for Bucket Ownership Configuration</p>
   */
  public Boolean isSkipBucketOwnershipValidationAndCopy() {
    return this.skipBucketOwnershipValidationAndCopy;
  }

  public void setSkipBucketOwnershipValidationAndCopy(Boolean skipBucketOwnershipValidationAndCopy) {
    this.skipBucketOwnershipValidationAndCopy = skipBucketOwnershipValidationAndCopy;
  }

  public Boolean isReplicationTimeControlEnabled() {
    return this.isReplicationTimeControlEnabled;
  }

  public void setReplicationTimeControlEnabled(Boolean isReplicationTimeControlEnabled) {
    this.isReplicationTimeControlEnabled = isReplicationTimeControlEnabled;
  }


  private static final int classNameHashCode =
      internalHashCodeCompute("com.amazon.bopspar.RuntimeConfig");

  /**
   * HashCode implementation for RuntimeConfig
   * based on java.util.Arrays.hashCode
   */
  @Override
  public int hashCode() {
    return internalHashCodeCompute(
        classNameHashCode,
        getDashboardUrl(),
        getManifestLocation(),
        isSkipBucketOwnershipValidationAndCopy(),
        isReplicationTimeControlEnabled());
  }

  private static int internalHashCodeCompute(Object... objects) {
    return Arrays.hashCode(objects);
  }

  /**
   * Equals implementation for RuntimeConfig
   * based on instanceof and Object.equals().
   */
  @Override
  public boolean equals(final Object other) {
    if (this == other) {
      return true;
    }

    if (!(other instanceof RuntimeConfig)) {
      return false;
    }

    RuntimeConfig that = (RuntimeConfig) other;

    return
        Objects.equals(getDashboardUrl(), that.getDashboardUrl())
            && Objects.equals(getManifestLocation(), that.getManifestLocation())
            && Objects.equals(isSkipBucketOwnershipValidationAndCopy(), that.isSkipBucketOwnershipValidationAndCopy())
            && Objects.equals(isReplicationTimeControlEnabled(), that.isReplicationTimeControlEnabled());
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
    ret.append("RuntimeConfig(");

    ret.append("dashboardUrl=");
    ret.append(String.valueOf(dashboardUrl));
    ret.append(", ");

    ret.append("manifestLocation=");
    ret.append(String.valueOf(manifestLocation));
    ret.append(", ");

    ret.append("skipBucketOwnershipValidationAndCopy=");
    ret.append(String.valueOf(skipBucketOwnershipValidationAndCopy));
    ret.append(", ");

    ret.append("isReplicationTimeControlEnabled=");
    ret.append(String.valueOf(isReplicationTimeControlEnabled));
    ret.append(")");

    return ret.toString();
  }
}

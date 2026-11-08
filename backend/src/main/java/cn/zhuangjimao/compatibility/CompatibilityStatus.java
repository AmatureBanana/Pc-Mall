package cn.zhuangjimao.compatibility;

/** Overall core diagnosis; sale, inventory and authorization are separate decisions. */
public enum CompatibilityStatus {
    CORE_PASSED, INCOMPATIBLE, INCOMPLETE, UNVERIFIED, ERROR
}

package cn.zhuangjimao.compatibility;

/** A request that cannot be interpreted as a configuration; rendered as 400 by the API advice. */
final class InvalidConfiguration extends RuntimeException {
    InvalidConfiguration(String message) { super(message); }
}

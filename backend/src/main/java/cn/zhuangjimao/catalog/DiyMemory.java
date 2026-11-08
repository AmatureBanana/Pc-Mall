package cn.zhuangjimao.catalog;

/** Quantity of one memory SKU in a DIY configuration. */
public final class DiyMemory {
    public static final int MAX_QUANTITY = 4;

    private DiyMemory() {}

    public static boolean validQuantity(long quantity, boolean memorySelected) {
        return quantity == 1 || memorySelected && quantity >= 2 && quantity <= MAX_QUANTITY;
    }
}

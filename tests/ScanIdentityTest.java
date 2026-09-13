package cn.dailyledger.app;
public final class ScanIdentityTest {
    public static void main(String[] args){String k=ScanIdentity.key("wechat","2026-09-06","18:07",345,"超市-购物");
        if(!k.equals(ScanIdentity.key("wechat","2026-09-06","18:07",345,"超市 购物")))throw new AssertionError("formatting must dedup");
        if(k.equals(ScanIdentity.key("alipay","2026-09-06","18:07",345,"超市购物")))throw new AssertionError("different channel");
        if(k.equals(ScanIdentity.key("wechat","2026-09-06","18:08",345,"超市购物")))throw new AssertionError("different time");
        if(k.equals(ScanIdentity.key("wechat","2026-09-07","18:07",345,"超市购物")))throw new AssertionError("different date");
        if(k.equals(ScanIdentity.key("wechat","2026-09-06","18:07",345,"另一个超市")))throw new AssertionError("different merchant");
        System.out.println("Scan identity: 5 checks passed");
    }
}

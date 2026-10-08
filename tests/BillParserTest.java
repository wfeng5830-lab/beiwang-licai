package cn.dailyledger.app;
import java.util.*;
public final class BillParserTest {
    static int checks;
    static void check(boolean b,String msg){checks++;if(!b)throw new AssertionError(msg);}
    static BillParser.Line line(String s,int x,int y,int width){return new BillParser.Line(s,x,y,x+width,y+30);}
    static void row(List<BillParser.Line> l,String name,String date,String amount,int y,String status){l.add(line(name,170,y,450));l.add(line(amount,735,y,100));l.add(line(date,170,y+60,350));if(status!=null)l.add(line(status,660,y+60,175));}
    static List<BillParser.Line> base(){return new ArrayList<>(Arrays.asList(line("账单",400,100,100),line("2026年9月",30,350,250),line("支出¥182.89 收入¥79.10",490,350,350)));}
    public static void main(String[] args){
        List<BillParser.Line> l=base();
        row(l,"群收款-转给重理-杨涛","9月6日 18:07","-3.45",480,null);
        row(l,"转账-退款","9月6日 18:07","+3.50",650,null);
        row(l,"转账-转给重理-杨涛","9月6日 18:06","-3.50",820,"对方已退还");
        row(l,"微信红包-来自Juttade","9月4日 18:07","+50.00",1000,null);
        row(l,"生活缴费","9月4日 09:03","-12.90",1170,null);
        row(l,"转账-转给蒋娜2.13","9月4日 09:02","-3.00",1350,null);
        row(l,"裕食轩空港园区二楼餐厅","9月1日 19:02","-8.00",1520,null);
        row(l,"生活缴费","9月1日 17:30","-152.04",1690,null);
        BillParser.Result result=BillParser.parse(l,"wechat","");
        check(result.pageRecognized,"page");check(result.bills.size()==5,"screenshot expects 5 rows, got "+result.bills.size());
        check(result.bills.stream().mapToLong(b->b.cents).sum()==17939,"net screenshot sum");
        check(result.bills.get(0).date.equals("2026-09-06"),"header year");
        check(result.bills.get(2).note.contains("蒋娜2.13"),"numeric merchant");
        List<BillParser.Line> plain=new ArrayList<>(l);plain.removeIf(x->x.text.equals("账单"));check(!BillParser.parse(plain,"wechat","2026-09").pageRecognized,"chat rejected");
        List<BillParser.Line> missing=new ArrayList<>(Arrays.asList(line("账单",400,100,100)));row(missing,"超市","9月6日 18:07","-23.50",480,null);
        check(BillParser.parse(missing,"wechat","").bills.get(0).date.isEmpty(),"missing year retained for review");check(BillParser.parse(missing,"wechat","2026-09").bills.get(0).date.isEmpty(),"saved month cannot invent year");check(BillParser.parse(missing,"wechat","2026-08").bills.size()==1,"wrong saved month cannot drop row");
        List<BillParser.Line> ali=new ArrayList<>(Arrays.asList(line("账单",400,100,100)));row(ali,"便利店","2026-09-08 12:31","-12.30",480,null);
        check(BillParser.parse(ali,"alipay","").bills.size()==1,"alipay full date");
        List<BillParser.Line> malformed=base();row(malformed,"早餐","9月6日 18:07","-1.234",480,null);check(BillParser.parse(malformed,"wechat","").bills.isEmpty(),"three decimals rejected");
        List<BillParser.Line> duplicate=base();row(duplicate,"早餐","9月6日 18:07","-2.50",480,null);row(duplicate,"早餐","9月6日 18:07","-2.50",650,null);check(BillParser.parse(duplicate,"wechat","").bills.size()==2,"parser preserves distinct rows");
        List<BillParser.Line> incomplete=base();row(incomplete,"早餐","9月6日","-2.50",480,null);check(BillParser.parse(incomplete,"wechat","").bills.get(0).time.isEmpty(),"missing time retained for review");
        List<BillParser.Line> modernAli=new ArrayList<>(Arrays.asList(line("搜索交易记录",160,130,350),line("全部 支出 转账 退款 订单 筛选",25,250,800),line("收支分析",660,430,170),line("¥373.86",50,360,200),line("2026年9月",25,310,250)));
        aliRow(modernAli,"余额宝-收益发放","今天 02:52","0.01",560,"投资理财");
        aliRow(modernAli,"余额宝-收益发放","昨天 02:57","0.01",800,"投资理财");
        aliRow(modernAli,"扫收钱码付款-给网络科技","09-08 22:11","-100.00",1035,"日用百货");
        aliRow(modernAli,"扫收钱码付款-给网络科技","09-08 21:46","-129.90",1270,"日用百货");
        aliRow(modernAli,"余额宝-收益发放","09-08 03:08","0.01",1505,"投资理财");
        aliRow(modernAli,"鑫盾百货超市","09-07 18:50","-6.96",1740,"日用百货");
        result=BillParser.parse(modernAli,"alipay","2026-09",java.time.LocalDate.of(2026,9,10));
        check(result.pageRecognized,"modern Alipay has search title instead of bill label");check(result.bills.size()==3,"modern Alipay expects three expenses, got "+result.bills.size());
        check(result.bills.stream().mapToLong(b->b.cents).sum()==23686,"modern Alipay amount total");check(result.bills.get(0).note.equals("扫收钱码付款-给网络科技"),"category excluded from merchant identity");
        check(result.bills.get(0).time.equals("22:11")&&result.bills.get(2).date.equals("2026-09-07"),"MM-dd timestamp");
        List<BillParser.Line> relativePage=base();aliRow(relativePage,"便利店","昨天 11:00","-2.00",480,"日用百货");
        result=BillParser.parse(relativePage,"alipay","2026-01",java.time.LocalDate.of(2026,1,1));check(result.bills.get(0).date.equals("2025-12-31"),"yesterday crosses year");
        List<BillParser.Line> autoWechat=new ArrayList<>(l);
        autoWechat.add(line("全部账单",40,240,220));autoWechat.add(line("查找交易",330,240,220));autoWechat.add(line("收支统计",680,240,160));
        result=BillParser.parseAuto(autoWechat,"2026-09");
        check(result.bills.size()==5&&result.bills.stream().allMatch(b->b.channel.equals("wechat")),"auto WeChat source and rows");
        result=BillParser.parseAuto(modernAli,"2026-09",java.time.LocalDate.of(2026,9,10));
        check(result.bills.size()==3&&result.bills.stream().allMatch(b->b.channel.equals("alipay")),"auto Alipay source and rows");
        check(result.bills.stream().mapToLong(b->b.cents).sum()==23686,"auto Alipay sum");
        check(BillParser.parseAuto(autoWechat,"2026-09").bills.get(0).channel.equals("wechat"),"switch back to WeChat without retained selection");
        check(!BillParser.parseAuto(missing,"2026-09").pageRecognized,"generic title alone cannot determine source");
        List<BillParser.Line> ambiguous=new ArrayList<>(autoWechat);ambiguous.add(line("搜索交易记录 筛选",20,200,700));
        check(!BillParser.parseAuto(ambiguous,"2026-09").pageRecognized,"conflicting source controls rejected");
        List<BillParser.Line> merchantMention=new ArrayList<>(missing);row(merchantMention,"搜索交易记录 筛选","9月6日 18:09","-8.00",780,null);
        check(!BillParser.parseAuto(merchantMention,"2026-09").pageRecognized,"transaction text cannot select source");
        List<BillParser.Line> absent=base();row(absent,"测试超市","","-18.00",480,null);
        result=BillParser.parse(absent,"wechat","2026-10");check(result.bills.size()==1&&result.bills.get(0).date.isEmpty()&&result.bills.get(0).time.isEmpty(),"amount/title without timestamp retained");
        List<BillParser.Line> partial=base();row(partial,"测试超市","12:40","-18.00",480,null);
        result=BillParser.parse(partial,"wechat","");check(result.bills.get(0).date.isEmpty()&&result.bills.get(0).time.equals("12:40"),"time only never fills date from month header");
        List<BillParser.Line> adjacent=base();row(adjacent,"缺日期店铺","","-18.00",480,null);row(adjacent,"另一店铺","9月3日 12:30","-8.00",650,null);
        result=BillParser.parse(adjacent,"wechat","");check(result.bills.size()==2&&result.bills.get(0).date.isEmpty()&&result.bills.get(1).date.equals("2026-09-03"),"no borrowing next expense date");
        List<BillParser.Line> mixed=base();row(mixed,"九月店铺","9月30日 12:30","-8.00",480,null);mixed.add(line("2026年10月",30,680,250));row(mixed,"十月店铺","10月1日 12:30","-9.00",770,null);
        result=BillParser.parse(mixed,"wechat","2025-01");check(result.bills.get(0).date.equals("2026-09-30")&&result.bills.get(1).date.equals("2026-10-01"),"separate screen month headers override stale setting");
        List<BillParser.Line> full=base();row(full,"历史店铺","2024-12-31 12:30","-8.00",480,null);result=BillParser.parse(full,"wechat","2026-09");check(result.bills.get(0).date.equals("2024-12-31"),"explicit historical date wins");
        List<BillParser.Line> invalidDate=base();row(invalidDate,"日期错误店铺","2026-02-30 12:30","-8.00",480,null);result=BillParser.parse(invalidDate,"wechat","");check(result.bills.get(0).date.isEmpty()&&!result.bills.get(0).dateHint.isEmpty(),"impossible date retained without guessing");
        List<BillParser.Line> relative=base();row(relative,"测试店铺","今天 12:30","-8.00",480,null);result=BillParser.parse(relative,"wechat","",java.time.LocalDate.of(2026,10,5));check(result.bills.get(0).date.equals("2026-10-05"),"today uses scan day not stale header");
        List<BillParser.Line> titleDate=base();row(titleDate,"9月3日主题店","","-8.00",480,null);result=BillParser.parse(titleDate,"wechat","");check(result.bills.get(0).date.isEmpty(),"date in merchant name is not transaction date");
        List<BillParser.Line> mismatch=base();row(mismatch,"测试店铺","8月3日 12:30","-8.00",480,null);result=BillParser.parse(mismatch,"wechat","");check(result.bills.get(0).date.isEmpty(),"conflicting month header requires review");
        List<BillParser.Line> noDateAli=new ArrayList<>(Arrays.asList(line("搜索交易记录",160,130,350),line("全部 支出 转账 退款 筛选",25,250,800)));
        aliRow(noDateAli,"测试便利店","","-8.00",480,"日用百货");aliRow(noDateAli,"余额宝-收益发放","今天 03:00","0.01",720,"投资理财");
        result=BillParser.parseAuto(noDateAli,"",java.time.LocalDate.of(2026,10,5));check(result.bills.size()==1&&result.bills.get(0).date.isEmpty(),"dateless Alipay expense recognized without taking next income date");
        List<BillParser.Line> combined=base();combined.add(line("合并文字测试店 -16.00",170,480,660));combined.add(line("9月6日 12:30",170,540,350));result=BillParser.parse(combined,"wechat","");check(result.bills.size()==1&&result.bills.get(0).cents==1600&&result.bills.get(0).note.equals("合并文字测试店"),"combined merchant/amount OCR line supported");
        List<BillParser.Line> mergedHeader=new ArrayList<>(Arrays.asList(line("账单",400,100,100),line("2026年9月 支出100.00",30,350,600)));row(mergedHeader,"测试店铺","9月6日 12:30","-8.00",480,null);result=BillParser.parse(mergedHeader,"wechat","");check(result.bills.get(0).date.equals("2026-09-06"),"month heading merged with summary supported");
        List<BillParser.Line> splitTime=base();row(splitTime,"测试店铺","9月6日","-8.00",480,null);splitTime.add(line("12:30",400,540,150));result=BillParser.parse(splitTime,"wechat","");check(result.bills.get(0).date.equals("2026-09-06")&&result.bills.get(0).time.equals("12:30"),"date and time split into OCR boxes supported");
        List<BillParser.Line> prefix=base();row(prefix,"示例商户","示例商户 |9月27日13:54","-14.70",480,null);
        result=BillParser.parse(prefix,"wechat","");
        check(result.bills.get(0).date.equals("2026-09-27")&&result.bills.get(0).time.equals("13:54"),"merged merchant/date metadata extracts both fields");
        check(result.bills.get(0).note.equals("示例商户"),"date text removed from merchant without duplicating title");
        List<BillParser.Line> prefixOnly=base();prefixOnly.add(line("-14.70",735,480,100));prefixOnly.add(line("示例商户 |9月27日13:54",170,540,450));
        result=BillParser.parse(prefixOnly,"wechat","");check(result.bills.size()==1&&result.bills.get(0).note.equals("示例商户")&&result.bills.get(0).date.equals("2026-09-27"),"merchant only present in merged metadata retained");
        List<BillParser.Line> dateOnly=base();row(dateOnly,"测试店","|9月27日","-5.00",480,null);
        result=BillParser.parse(dateOnly,"wechat","");check(!result.bills.get(0).incomplete()&&result.bills.get(0).time.isEmpty()&&!result.bills.get(0).hasExactIdentity(),"date-only bill bookable but not an exact duplicate identity");
        List<BillParser.Line> noYear=new ArrayList<>(Arrays.asList(line("账单",400,100,100)));row(noYear,"测试店","测试店 |10月2日19:14","-45.00",480,null);
        result=BillParser.parse(noYear,"wechat","");check(result.bills.get(0).date.isEmpty()&&result.bills.get(0).dateHint.contains("10月2日")&&result.bills.get(0).dateHint.contains("年份"),"missing year preserves observed month/day in hint");
        List<BillParser.Line> nameDate=base();row(nameDate,"测试店","9月3日主题店","-8.00",480,null);
        result=BillParser.parse(nameDate,"wechat","");check(result.bills.get(0).date.isEmpty(),"metadata merchant suffix is not a timestamp");
        List<BillParser.Line> aliPrefix=base();aliRow(aliPrefix,"测试店","|2026-09-27","-5.00",480,"日用百货");
        result=BillParser.parse(aliPrefix,"alipay","");check(result.bills.get(0).date.equals("2026-09-27")&&!result.bills.get(0).incomplete(),"Alipay date-only prefixed metadata supported");
        System.out.println("Bill parser: "+checks+" checks passed");
    }
    static void aliRow(List<BillParser.Line> rows,String merchant,String date,String amount,int y,String category){rows.add(line(merchant,155,y,475));rows.add(line(amount,695,y,130));rows.add(line(category,155,y+60,250));rows.add(line(date,155,y+120,330));}
}

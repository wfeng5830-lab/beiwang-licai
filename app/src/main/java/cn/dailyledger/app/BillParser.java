package cn.dailyledger.app;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Expense rows are anchored by amounts; incomplete dates remain review candidates. */
public final class BillParser {
    public static final class Line {
        public final String text;public final int left,top,right,bottom;
        public Line(String t,int l,int y,int r,int b){text=Normalizer.normalize(t,Normalizer.Form.NFKC).replace('−','-').replace('—','-').replace('：',':');left=l;top=y;right=r;bottom=b;}
        int cy(){return (top+bottom)/2;}
    }
    public static final class Bill {
        public final String date,time,note,channel,dateHint;public final long cents;
        public Bill(String d,String t,String n,String c,long a){this(d,t,n,c,a,"");}
        public Bill(String d,String t,String n,String c,long a,String hint){date=d;time=t;note=n;channel=c;cents=a;dateHint=hint;}
        public boolean incomplete(){return date.isEmpty();}
        public boolean hasExactIdentity(){return !date.isEmpty()&&!time.isEmpty();}
    }
    public static final class Result {
        public final List<Bill> bills;public final int ignored;public final boolean pageRecognized;public final String message;
        Result(List<Bill>b,int i,boolean p,String m){bills=b;ignored=i;pageRecognized=p;message=m;}
    }
    private static final Pattern HEADER=Pattern.compile("(20\\d{2})\\s*[年/-]\\s*(1[0-2]|0?[1-9])(?:\\s*月|(?=$|\\s))");
    private static final Pattern DATE=Pattern.compile("(?<!\\d)(?:(20\\d{2})\\s*[年/.-]\\s*)?(1[0-2]|0?[1-9])\\s*[月/.-]\\s*(3[01]|[12]\\d|0?[1-9])(?!\\d)(?:\\s*日)?");
    private static final Pattern TIME=Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])\\s*:\\s*([0-5]\\d)(?:\\s*:\\s*[0-5]\\d)?(?!\\d)");
    private static final Pattern AMOUNT=Pattern.compile("([+-])\\s*[¥￥]?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d{1,7})\\.\\d{2})(?![\\d.])");
    private static final Pattern EXCLUDE=Pattern.compile("退还|已退|退款|交易关闭|交易失败|支付失败|待付款|待支付|已取消|已撤销|部分退");
    private static final Pattern MONEY_LINE=Pattern.compile("^[+-]?\\s*[¥￥]?\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d{1,7})\\.\\d{2}$");
    private static final Pattern CATEGORY=Pattern.compile("日用百货|投资理财|餐饮美食|交通出行|生活服务|生活缴费|服饰装扮|充值缴费|其他|医疗健康|文化休闲|数码电器|商业服务");
    private static boolean amountLine(Line l){
        return MONEY_LINE.matcher(l.text.trim()).matches();
    }
    private static List<Line> separateAmounts(List<Line> input){
        List<Line> result=new ArrayList<>();
        for(Line line:input){
            Matcher m=AMOUNT.matcher(line.text);
            if(!amountLine(line)&&m.find()&&m.end()==line.text.trim().length()&&!line.text.matches(".*(支出|收入|合计|总计).*")){
                String title=line.text.substring(0,m.start()).trim();
                if(!title.isEmpty()){
                    int split=Math.max(line.left+1,line.right-Math.max(1,line.bottom-line.top)*m.group().length()/2);
                    result.add(new Line(title,line.left,line.top,split,line.bottom));
                    result.add(new Line(m.group(),split,line.top,line.right,line.bottom));continue;
                }
            }
            result.add(line);
        }
        return result;
    }
    public static Result parseAuto(List<Line> input){return parseAuto(input,"",LocalDate.now());}
    // Legacy parameter deliberately ignored: saved scanner month must never assign a transaction date.
    public static Result parseAuto(List<Line> input,String unusedMonth){return parseAuto(input,unusedMonth,LocalDate.now());}
    public static Result parseAuto(List<Line> input,String unusedMonth,LocalDate today){
        input=separateAmounts(input);
        int firstRow=input.stream().filter(BillParser::amountLine).mapToInt(l->l.top).min().orElse(Integer.MAX_VALUE);
        String header=String.join(" ",input.stream().filter(l->l.bottom<firstRow).map(l->l.text.replaceAll("\\s","")).toArray(String[]::new));
        boolean wechat=header.contains("微信账单")||(header.contains("全部账单")&&(header.contains("查找交易")||header.contains("收支统计")));
        boolean alipay=header.contains("支付宝账单")||(header.contains("搜索交易记录")&&(header.contains("筛选")||header.contains("收支分析")));
        if(wechat==alipay)return new Result(new ArrayList<>(),0,false,"未能确定账单来源，请显示列表顶部的搜索栏和筛选栏后重扫");
        return parse(input,wechat?"wechat":"alipay","",today);
    }
    public static Result parse(List<Line> input,String channel,String unusedMonth){return parse(input,channel,unusedMonth,LocalDate.now());}
    public static Result parse(List<Line> input,String channel,String unusedMonth,LocalDate today){
        if(!Arrays.asList("wechat","alipay").contains(channel))return new Result(new ArrayList<>(),0,false,"不支持的支付方式");
        input=separateAmounts(input);
        List<Line> lines=new ArrayList<>();
        for(Line l:input){if(l.text.trim().isEmpty())continue;boolean duplicate=false;for(Line x:lines)if(x.text.equals(l.text)&&Math.abs(x.cy()-l.cy())<5&&Math.abs(x.left-l.left)<10){duplicate=true;break;}if(!duplicate)lines.add(l);}
        lines.removeIf(l->lines.stream().anyMatch(x->x!=l&&l.top<=x.top&&l.bottom>=x.bottom&&l.bottom-l.top>2*(x.bottom-x.top)&&l.text.contains(x.text)));
        lines.sort(Comparator.comparingInt((Line l)->l.top).thenComparingInt(l->l.left));
        boolean page=lines.stream().anyMatch(l->l.text.replaceAll("\\s","").matches("(微信|支付宝)?(全部)?账单|账单明细"));
        if("alipay".equals(channel)){String text=String.join(" ",lines.stream().map(l->l.text).toArray(String[]::new));page=page||(text.contains("搜索交易记录")&&text.contains("筛选")&&(text.contains("收支分析")||(text.contains("支出")&&text.contains("转账")&&text.contains("退款"))));}
        if(!page)return new Result(new ArrayList<>(),0,false,"未找到账单列表标题");
        // Unsigned income amounts also form boundaries, so their dates cannot leak into an expense.
        List<Line> amounts=new ArrayList<>();for(Line l:lines)if(amountLine(l))amounts.add(l);
        List<Bill> bills=new ArrayList<>();int ignored=0;
        for(int i=0;i<amounts.size();i++){
            Line amount=amounts.get(i);Matcher money=AMOUNT.matcher(amount.text);
            if(!money.find()||!"-".equals(money.group(1)))continue;
            long cents;try{cents=new BigDecimal(money.group(2).replace(",","")).movePointRight(2).longValueExact();}catch(Exception e){ignored++;continue;}
            if(cents<=0||cents>999999999L){ignored++;continue;}
            int h=Math.max(10,amount.bottom-amount.top),from=amount.top-h/2;
            int to=amount.bottom+("alipay".equals(channel)?7:5)*h;
            if(i+1<amounts.size())to=Math.min(to,amounts.get(i+1).top-Math.max(10,amounts.get(i+1).bottom-amounts.get(i+1).top)/2);
            for(Line l:lines)if(l.top>amount.bottom&&HEADER.matcher(l.text.trim()).matches())to=Math.min(to,l.top);
            List<Line> row=new ArrayList<>();for(Line l:lines)if(l!=amount&&l.cy()>=from&&l.cy()<to)row.add(l);
            String all=String.join(" ",row.stream().map(l->l.text).toArray(String[]::new));
            if(EXCLUDE.matcher(all).find()){ignored++;continue;}
            StringBuilder note=new StringBuilder();String rawDate="",clock="";boolean ambiguousDate=false,ambiguousTime=false;
            for(Line l:row){
                String t=l.text.trim();
                // Dates are metadata below the title, not numbers embedded in merchant names.
                boolean metadata=l.cy()>amount.cy()+h/2;
                Matcher dm=DATE.matcher(t),tm=TIME.matcher(t);
                // OCR may join a merchant/separator with its timestamp. Only accept a
                // date in the metadata area with no trailing merchant text.
                boolean matched=metadata&&dm.find()&&t.substring(dm.end()).trim().matches("(?:"+TIME.pattern()+")?");
                boolean dated=matched||metadata&&(t.startsWith("今天")||t.startsWith("昨天"));
                if(dated){
                    String value=matched?t.substring(dm.start()):t;
                    if(!rawDate.isEmpty()&&!rawDate.equals(value))ambiguousDate=true;else rawDate=value;
                    if(matched&&dm.start()>0&&l.left<amount.left&&l.cy()<=amount.cy()+2*h){
                        String prefix=t.substring(0,dm.start()).replaceAll("[\\p{P}\\p{S}\\s丨]+$","").trim();
                        if(!prefix.isEmpty()&&!note.toString().equals(prefix)){if(note.length()>0)note.append(' ');note.append(prefix);}
                    }
                }
                if(metadata&&tm.find()){String value=String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(tm.group(1)),tm.group(2));if(!clock.isEmpty()&&!clock.equals(value))ambiguousTime=true;clock=value;}
                if(dated||TIME.matcher(t).matches()||HEADER.matcher(t).matches()||amountLine(l)||t.matches(".*(账单|收入|支出|收支统计|查找交易|搜索交易记录|筛选).*"))continue;
                if("alipay".equals(channel)&&metadata&&CATEGORY.matcher(t).matches())continue;
                if(l.left<amount.left&&l.cy()<=amount.cy()+2*h){if(note.length()>0)note.append(' ');note.append(t);}
            }
            if(note.length()==0){ignored++;continue;}
            String date=ambiguousDate?"":resolveDate(rawDate,lines,amount,today);
            String hint="";
            if(date.isEmpty()){
                Matcher hintDate=DATE.matcher(rawDate);
                hint=ambiguousDate?"发现多个日期，请核对":rawDate.isEmpty()?"未识别到日期":
                    rawDate+(hintDate.lookingAt()&&hintDate.group(1)==null?"（缺少匹配的年份或月份）":"（日期无效，请核对）");
            }
            if(hint.length()>80)hint=hint.substring(0,80);
            String merchant=note.toString().trim();if(merchant.length()>80)merchant=merchant.substring(0,80);
            bills.add(new Bill(date,ambiguousTime?"":clock,merchant,channel,cents,hint));
        }
        return new Result(bills,ignored,true,bills.isEmpty()?"没有找到金额和商户完整的支出行":"已识别 "+bills.size()+" 笔支出");
    }
    private static String resolveDate(String raw,List<Line> lines,Line amount,LocalDate today){
        if(raw.startsWith("今天"))return today.toString();
        if(raw.startsWith("昨天"))return today.minusDays(1).toString();
        Matcher d=DATE.matcher(raw);if(!d.lookingAt())return "";
        int month=Integer.parseInt(d.group(2)),day=Integer.parseInt(d.group(3));String year=d.group(1);
        if(year==null){
            String headerYear=null;int headerMonth=0;
            for(Line line:lines){if(line.top>=amount.top)break;Matcher hm=HEADER.matcher(line.text.trim());if(hm.lookingAt()&&!DATE.matcher(line.text).find()){headerYear=hm.group(1);headerMonth=Integer.parseInt(hm.group(2));}}
            if(headerYear==null||headerMonth!=month)return "";year=headerYear;
        }
        try{return LocalDate.of(Integer.parseInt(year),month,day).toString();}catch(Exception e){return "";}
    }
}

package cn.dailyledger.app;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative geometric list parser. Missing dates/times and ambiguous rows are skipped. */
public final class BillParser {
    public static final class Line {
        public final String text;public final int left,top,right,bottom;
        public Line(String t,int l,int y,int r,int b){text=Normalizer.normalize(t,Normalizer.Form.NFKC).replace('−','-').replace('—','-').replace('：',':');left=l;top=y;right=r;bottom=b;}
        int cy(){return (top+bottom)/2;}
    }
    public static final class Bill {
        public final String date,time,note,channel;public final long cents;
        public Bill(String d,String t,String n,String c,long a){date=d;time=t;note=n;channel=c;cents=a;}
    }
    public static final class Result {
        public final List<Bill> bills;public final int ignored;public final boolean pageRecognized;public final String message;
        Result(List<Bill>b,int i,boolean p,String m){bills=b;ignored=i;pageRecognized=p;message=m;}
    }
    private static final Pattern HEADER=Pattern.compile("(20\\d{2})\\s*[年/-]\\s*(1[0-2]|0?[1-9])(?:\\s*月|(?=$|\\s))");
    private static final Pattern DATE=Pattern.compile("(?<!\\d)(?:(20\\d{2})\\s*[年/.-]\\s*)?(1[0-2]|0?[1-9])\\s*[月/.-]\\s*(3[01]|[12]\\d|0?[1-9])(?:\\s*日)?(?!\\d)");
    private static final Pattern TIME=Pattern.compile("(?<!\\d)([01]?\\d|2[0-3])\\s*:\\s*([0-5]\\d)(?:\\s*:\\s*[0-5]\\d)?(?!\\d)");
    private static final Pattern AMOUNT=Pattern.compile("([+-])\\s*[¥￥]?\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d{1,7})\\.\\d{2})(?![\\d.])");
    private static final Pattern EXCLUDE=Pattern.compile("退还|已退|退款|交易关闭|交易失败|支付失败|待付款|待支付|已取消|已撤销|部分退");
    private static final class Anchor {Line line;String date,time;Anchor(Line l,String d,String t){line=l;date=d;time=t;}}
    public static Result parseAuto(List<Line> input,String fallbackMonth){
        return parseAuto(input,fallbackMonth,LocalDate.now());
    }
    public static Result parseAuto(List<Line> input,String fallbackMonth,LocalDate today){
        // Identify the list controls above transactions, never a merchant mentioning a payment app.
        int firstRow=input.stream().filter(l->TIME.matcher(l.text).find()&&(DATE.matcher(l.text).find()||l.text.contains("今天")||l.text.contains("昨天"))).mapToInt(l->l.top).min().orElse(Integer.MAX_VALUE);
        String header=String.join(" ",input.stream().filter(l->l.bottom<firstRow).map(l->l.text.replaceAll("\\s","")).toArray(String[]::new));
        boolean wechat=header.contains("微信账单")||(header.contains("全部账单")&&(header.contains("查找交易")||header.contains("收支统计")));
        boolean alipay=header.contains("支付宝账单")||(header.contains("搜索交易记录")&&(header.contains("筛选")||header.contains("收支分析")));
        if(wechat==alipay)return new Result(new ArrayList<>(),0,false,"未能确定账单来源，请显示列表顶部的搜索栏和筛选栏后重扫");
        return parse(input,wechat?"wechat":"alipay",fallbackMonth,today);
    }
    public static Result parse(List<Line> input,String channel,String fallbackMonth){
        return parse(input,channel,fallbackMonth,LocalDate.now());
    }
    public static Result parse(List<Line> input,String channel,String fallbackMonth,LocalDate today){
        if(!Arrays.asList("wechat","alipay").contains(channel))return new Result(new ArrayList<>(),0,false,"不支持的支付方式");
        List<Line> lines=new ArrayList<>();
        for(Line l:input){if(l.text.trim().isEmpty())continue;boolean duplicate=false;for(Line x:lines)if(x.text.equals(l.text)&&Math.abs(x.cy()-l.cy())<5&&Math.abs(x.left-l.left)<10){duplicate=true;break;}if(!duplicate)lines.add(l);}
        // Parent accessibility nodes repeating child contents are not separate rows.
        lines.removeIf(l->lines.stream().anyMatch(x->x!=l&&l.top<=x.top&&l.bottom>=x.bottom&&l.bottom-l.top>2*(x.bottom-x.top)&&l.text.contains(x.text)));
        lines.sort(Comparator.comparingInt((Line l)->l.top).thenComparingInt(l->l.left));
        boolean page=lines.stream().anyMatch(l->l.text.replaceAll("\\s","").matches("(微信|支付宝)?(全部)?账单|账单明细"));
        if("alipay".equals(channel)){String text=String.join(" ",lines.stream().map(l->l.text).toArray(String[]::new));page=page||(text.contains("搜索交易记录")&&text.contains("筛选")&&(text.contains("收支分析")||(text.contains("支出")&&text.contains("转账")&&text.contains("退款"))));}
        if(!page)return new Result(new ArrayList<>(),0,false,"未找到账单列表标题");
        List<Anchor> anchors=new ArrayList<>();int ignored=0;
        for(Line l:lines){
            Matcher date=DATE.matcher(l.text);boolean hasDate=date.find();boolean relative=l.text.contains("今天")||l.text.contains("昨天");if(!hasDate&&!relative)continue;
            Matcher time=TIME.matcher(l.text);String clock=null;
            if(time.find())clock=String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(time.group(1)),time.group(2));
            else for(Line x:lines){if(x==l||Math.abs(x.cy()-l.cy())>Math.max(8,(l.bottom-l.top)/2))continue;Matcher t=TIME.matcher(x.text);if(t.find()){clock=String.format(Locale.ROOT,"%02d:%s",Integer.parseInt(t.group(1)),t.group(2));break;}}
            if(clock==null)continue;
            if(relative){anchors.add(new Anchor(l,(l.text.contains("昨天")?today.minusDays(1):today).toString(),clock));continue;}
            int m=Integer.parseInt(date.group(2)),day=Integer.parseInt(date.group(3));String year=date.group(1);
            if(year==null){String header=null;for(Line h:lines){if(h.top>l.top)break;if(TIME.matcher(h.text).find()||DATE.matcher(h.text).find())continue;Matcher hm=HEADER.matcher(h.text);if(hm.find())header=hm.group(1)+"-"+String.format(Locale.ROOT,"%02d",Integer.parseInt(hm.group(2)));}
                String context=header!=null?header:fallbackMonth;
                if(context==null||!context.matches("20\\d\\d-\\d{2}")||Integer.parseInt(context.substring(5))!=m){ignored++;continue;}year=context.substring(0,4);
            }
            try{String d=LocalDate.of(Integer.parseInt(year),m,day).toString();anchors.add(new Anchor(l,d,clock));}catch(Exception e){ignored++;}
        }
        List<Bill> bills=new ArrayList<>();
        for(int i=0;i<anchors.size();i++){
            Anchor a=anchors.get(i);int height=Math.max(10,a.line.bottom-a.line.top);
            // Transaction title/amount normally sit one text line above the timestamp.
            boolean ali="alipay".equals(channel);
            int from=a.line.top-(ali?6:3)*height,to=a.line.bottom+(ali?1:2)*height;
            if(i>0)from=Math.max(from,ali?anchors.get(i-1).line.bottom+height:(anchors.get(i-1).line.bottom+a.line.top)/2);
            if(i+1<anchors.size())to=Math.min(to,ali?anchors.get(i+1).line.top-4*height:(a.line.bottom+anchors.get(i+1).line.top)/2);
            List<Line> row=new ArrayList<>();for(Line l:lines)if(l.cy()>=from&&l.cy()<=to)row.add(l);
            String all=String.join(" ",row.stream().map(l->l.text).toArray(String[]::new));
            if(EXCLUDE.matcher(all).find()){ignored++;continue;}
            long cents=0;boolean invalid=false;StringBuilder note=new StringBuilder();
            for(Line l:row){Matcher amount=AMOUNT.matcher(l.text);while(amount.find()){
                if(!"-".equals(amount.group(1))){invalid=true;break;}
                try{long value=new BigDecimal(amount.group(2).replace(",","")).movePointRight(2).longValueExact();if(value<=0||value>999999999L||(cents!=0&&cents!=value))invalid=true;cents=value;}catch(Exception e){invalid=true;}
            }
                String n=AMOUNT.matcher(l.text).replaceAll("").trim();
                if(n.isEmpty()||DATE.matcher(n).replaceAll("").trim().isEmpty()||TIME.matcher(n).find()||HEADER.matcher(n).find()||n.matches(".*(账单|收入|支出|收支统计|查找交易).*"))continue;
                if(ali&&n.matches("日用百货|投资理财|餐饮美食|交通出行|生活服务|生活缴费|服饰装扮|充值缴费|其他|医疗健康|文化休闲|数码电器"))continue;
                if(l.cy()<=a.line.cy()){if(note.length()>0)note.append(' ');note.append(n);}
            }
            if(invalid||cents==0||note.length()==0){ignored++;continue;}
            String merchant=note.toString().trim();if(merchant.length()>80)merchant=merchant.substring(0,80);
            bills.add(new Bill(a.date,a.time,merchant,channel,cents));
        }
        return new Result(bills,ignored,true,bills.isEmpty()?"没有找到金额、日期和时间完整的支出行":"已识别 "+bills.size()+" 笔支出");
    }
}

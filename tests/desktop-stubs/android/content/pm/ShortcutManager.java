package android.content.pm;
import java.util.*;
public class ShortcutManager {
    public boolean supported=true,acceptRequest=true,acceptUpdate=true,throwUpdate=false;
    public int pinCalls,updateCalls;
    public ShortcutInfo requested,updated;
    public final List<ShortcutInfo> pinned=new ArrayList<>();
    public List<ShortcutInfo> getPinnedShortcuts(){return pinned;}
    public boolean isRequestPinShortcutSupported(){return supported;}
    public boolean requestPinShortcut(ShortcutInfo info,Object callback){pinCalls++;requested=info;return acceptRequest;}
    public boolean updateShortcuts(List<ShortcutInfo> list){updateCalls++;if(throwUpdate)throw new IllegalStateException();updated=list.get(0);return acceptUpdate;}
}

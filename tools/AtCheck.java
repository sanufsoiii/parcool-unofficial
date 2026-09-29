import net.neoforged.accesstransformer.parser.AccessTransformerList;
import java.nio.file.Path;

public class AtCheck {
    public static void main(String[] args) throws Exception {
        AccessTransformerList list = new AccessTransformerList();
        list.loadFromPath(Path.of(args[0]));
        var all = list.getAccessTransformers();
        int n = 0;
        for (var e : all.entrySet()) for (var t : e.getValue()) n++;
        System.out.println("parsed OK, " + n + " transformers from " + args[0]);
    }
}

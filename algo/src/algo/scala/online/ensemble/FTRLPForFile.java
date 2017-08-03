package algo.scala.online.ensemble;


import algo.java.online.impl.FTRLP;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by root on 17-7-31.
 */
public class FTRLPForFile {
    public static void main(String[] args) throws IOException {
        int a = 3599;
        predict(3599);
    }


    public static void predict(int sumLeaf) throws IOException {
        String[] strs = new String[sumLeaf];

        for(int i = 1;i < sumLeaf + 1;i++){
            strs[i-1] = i+"";
        }

        Map<String, Object> meta = new HashMap<>(3);
        meta.put("targetColumn", "0");
        meta.put("idColumn", "id");
        meta.put("numberColumns",  new String[]{});
        meta.put("categoricalColumns",strs);
        String path = "/root/ss/s1/part-00000";
        FTRLP ftrlp = new FTRLP(meta,1.1,1,0.03,1);
        ftrlp.fitForFile(path);

        path = "/root/ss/s2/part-00000";
        FileWriter fw = new FileWriter("opt.csv");
        fw.write(ftrlp.forFile(path));
        fw.close();
    }
}

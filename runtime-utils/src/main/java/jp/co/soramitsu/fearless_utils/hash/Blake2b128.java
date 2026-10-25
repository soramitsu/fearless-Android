package jp.co.soramitsu.fearless_utils.hash;

import org.bouncycastle.crypto.digests.Blake2bDigest;
import org.bouncycastle.jcajce.provider.digest.BCMessageDigest;

public class Blake2b128
        extends BCMessageDigest
        implements Cloneable {
    public Blake2b128() {
        super(new Blake2bDigest(128));
    }

    public Object clone()
            throws CloneNotSupportedException {
        Blake2b128 d = (Blake2b128) super.clone();
        d.digest = new Blake2bDigest((Blake2bDigest) digest);

        return d;
    }
}
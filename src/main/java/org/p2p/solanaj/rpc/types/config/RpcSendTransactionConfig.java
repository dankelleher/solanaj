package org.p2p.solanaj.rpc.types.config;

import com.squareup.moshi.Json;

public class RpcSendTransactionConfig {

    public static enum Encoding {
        base64("base64");

        private String enc;

        Encoding(String enc) {
            this.enc = enc;
        }

        public String getEncoding() {
            return enc;
        }

    }

    public RpcSendTransactionConfig(Encoding encoding, boolean skipPreFlight) {
        this.encoding = encoding;
        this.skipPreFlight = skipPreFlight;
    }

    public RpcSendTransactionConfig() {}

    @Json(name = "encoding")
    private Encoding encoding = Encoding.base64;

    @Json(name ="skipPreflight")
    private boolean skipPreFlight = true;

    public static class RpcSendTransactionConfigBuilder {
        private Encoding encoding = Encoding.base64;
        private boolean skipPreFlight = true;

        public RpcSendTransactionConfigBuilder setEncoding(Encoding encoding) {
            this.encoding = encoding;
            return this;
        }

        public RpcSendTransactionConfigBuilder setSkipPreFlight(boolean skipPreFlight) {
            this.skipPreFlight = skipPreFlight;
            return this;
        }

        public RpcSendTransactionConfig build() {
            return new RpcSendTransactionConfig(encoding, skipPreFlight);
        }
    }

    public static RpcSendTransactionConfigBuilder builder() {
        return new RpcSendTransactionConfigBuilder();
    }
}

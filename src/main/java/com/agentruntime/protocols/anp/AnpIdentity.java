package com.agentruntime.protocols.anp;
/** V-13 fix: DID-based identity for ANP. Vol1 Appendix A §"ANP Protocol". */
public record AnpIdentity(String did, String verificationMethod, String publicKeyJwk) {
    public static AnpIdentity of(String did) {
        return new AnpIdentity(did, did + "#keys-1", "{}");
    }
}

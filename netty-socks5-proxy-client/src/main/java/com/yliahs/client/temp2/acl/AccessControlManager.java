package com.yliahs.client.temp2.acl;

import com.yliahs.client.temp2.config.ProxyConfiguration;
import io.netty.util.NetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 访问控制列表 (ACL) 管理。
 */
public class AccessControlManager {

    private static final Logger log = LoggerFactory.getLogger(AccessControlManager.class);

    private final List<Rule> allowRules;
    private final List<Rule> denyRules;
    private final boolean defaultAllow;

    public AccessControlManager(ProxyConfiguration.AccessControl aclConfig) {
        Objects.requireNonNull(aclConfig, "aclConfig");
        this.allowRules = buildRules(aclConfig.getAllow());
        this.denyRules = buildRules(aclConfig.getDeny());
        this.defaultAllow = aclConfig.isDefaultAllow();
    }

    private static boolean wildcardMatches(String input, String wildcard) {
        StringBuilder regex = new StringBuilder("^");
        for (char c : wildcard.toCharArray()) {
            if (c == '*') {
                regex.append(".*");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        regex.append('$');
        return input.matches(regex.toString());
    }

    public boolean isAllowed(String targetHost) {
        if (targetHost == null || targetHost.isBlank()) {
            return false;
        }
        String normalized = targetHost.toLowerCase(Locale.ROOT);

        for (Rule deny : denyRules) {
            if (deny.matches(normalized)) {
                log.debug("ACL deny match: rule={}, host={}", deny, normalized);
                return false;
            }
        }

        if (!allowRules.isEmpty()) {
            for (Rule allow : allowRules) {
                if (allow.matches(normalized)) {
                    return true;
                }
            }
            log.debug("ACL allow list provided but no rule matched host={}", normalized);
            return false;
        }

        return defaultAllow;
    }

    private List<Rule> buildRules(List<String> entries) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }
        List<Rule> rules = new ArrayList<>();
        for (String entry : entries) {
            Rule rule = Rule.parse(entry);
            if (rule != null) {
                rules.add(rule);
            }
        }
        return Collections.unmodifiableList(rules);
    }

    private interface Rule {
        static Rule parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return null;
            }
            String trimmed = raw.trim().toLowerCase(Locale.ROOT);
            if (trimmed.contains("/")) {
                return CidrRule.from(trimmed);
            }
            if (trimmed.contains("*")) {
                return new WildcardDomainRule(trimmed);
            }
            if (NetUtil.isValidIpV4Address(trimmed) || NetUtil.isValidIpV6Address(trimmed)) {
                return new IpLiteralRule(trimmed);
            }
            return new ExactDomainRule(trimmed);
        }

        boolean matches(String host);
    }

    private static final class IpLiteralRule implements Rule {
        private final String ip;

        private IpLiteralRule(String ip) {
            this.ip = ip;
        }

        @Override
        public boolean matches(String host) {
            return host.equals(ip);
        }

        @Override
        public String toString() {
            return "IpLiteralRule{" + "ip='" + ip + '\'' + '}';
        }
    }

    private static final class WildcardDomainRule implements Rule {
        private final String wildcard;

        private WildcardDomainRule(String wildcard) {
            this.wildcard = wildcard;
        }

        @Override
        public boolean matches(String host) {
            return wildcardMatches(host, wildcard);
        }

        @Override
        public String toString() {
            return "WildcardDomainRule{" + "wildcard='" + wildcard + '\'' + '}';
        }
    }

    private static final class ExactDomainRule implements Rule {
        private final String domain;

        private ExactDomainRule(String domain) {
            this.domain = domain;
        }

        @Override
        public boolean matches(String host) {
            return host.equals(domain);
        }

        @Override
        public String toString() {
            return "ExactDomainRule{" + "domain='" + domain + '\'' + '}';
        }
    }

    private static final class CidrRule implements Rule {
        private final byte[] network;
        private final int prefixLength;

        private CidrRule(byte[] network, int prefixLength) {
            this.network = network;
            this.prefixLength = prefixLength;
        }

        static Rule from(String value) {
            String[] parts = value.split("/", 2);
            if (parts.length != 2) {
                return null;
            }
            byte[] networkBytes = NetUtil.createByteArrayFromIpAddressString(parts[0]);
            if (networkBytes == null) {
                return null;
            }
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }

            int maxPrefix = networkBytes.length * 8;
            if (prefix < 0 || prefix > maxPrefix) {
                return null;
            }
            return new CidrRule(networkBytes, prefix);
        }

        @Override
        public boolean matches(String host) {
            byte[] target = NetUtil.createByteArrayFromIpAddressString(host);
            if (target == null || target.length != network.length) {
                return false;
            }
            int fullBytes = prefixLength / 8;
            int remainder = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (target[i] != network[i]) {
                    return false;
                }
            }
            if (remainder == 0) {
                return true;
            }
            int mask = 0xFF << (8 - remainder);
            return (target[fullBytes] & mask) == (network[fullBytes] & mask);
        }

        @Override
        public String toString() {
            return "CidrRule{prefixLength=" + prefixLength + '}';
        }
    }
}


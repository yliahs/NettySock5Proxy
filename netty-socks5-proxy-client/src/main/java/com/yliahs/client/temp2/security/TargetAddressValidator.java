package com.yliahs.client.temp2.security;

import com.yliahs.client.temp2.config.ProxyConfiguration;
import io.netty.handler.codec.socksx.v5.Socks5AddressType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

/**
 * 目标地址安全校验。
 */
public class TargetAddressValidator {

    private static final Logger log = LoggerFactory.getLogger(TargetAddressValidator.class);

    private final boolean blockPrivateNetworks;
    private final boolean allowLoopback;
    private final List<CidrBlock> blockedCidrs;
    private final Set<Integer> blockedPorts;

    public TargetAddressValidator(ProxyConfiguration.Security security) {
        Objects.requireNonNull(security, "security");
        this.blockPrivateNetworks = security.isBlockPrivateNetworks();
        this.allowLoopback = security.isAllowLoopback();
        this.blockedCidrs = buildCidrs(security.getBlockedSubnets());
        this.blockedPorts = new HashSet<>(security.getBlockedPorts());
    }

    private List<CidrBlock> buildCidrs(List<String> entries) {
        List<CidrBlock> cidrs = new ArrayList<>();
        for (String entry : entries) {
            CidrBlock block = CidrBlock.parse(entry);
            if (block != null) {
                cidrs.add(block);
            } else {
                log.warn("忽略无效的 CIDR 配置: {}", entry);
            }
        }
        return cidrs;
    }

    public boolean isPortBlocked(int port) {
        return blockedPorts.contains(port);
    }

    public boolean isBlockedBeforeConnect(Socks5AddressType type, String host) {
        if (!blockPrivateNetworks) {
            return false;
        }
        if (type == Socks5AddressType.IPv4 || type == Socks5AddressType.IPv6) {
            return isIpBlocked(host);
        }
        return false;
    }

    public boolean isBlockedAfterConnect(InetAddress address) {
        if (!blockPrivateNetworks || address == null) {
            return false;
        }
        if (!allowLoopback && address.isLoopbackAddress()) {
            return true;
        }
        if (address.isAnyLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }
        if (address.isSiteLocalAddress()) {
            return true;
        }
        return blockedCidrs.stream().anyMatch(cidr -> cidr.contains(address));
    }

    private boolean isIpBlocked(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return isBlockedAfterConnect(address);
        } catch (UnknownHostException e) {
            log.debug("无法解析地址 {} 用于安全校验，默认允许", host, e);
            return false;
        }
    }

    private static final class CidrBlock {
        private final byte[] network;
        private final byte[] mask;

        private CidrBlock(byte[] network, byte[] mask) {
            this.network = network;
            this.mask = mask;
        }

        static CidrBlock parse(String cidr) {
            if (cidr == null || cidr.isBlank()) {
                return null;
            }
            String[] parts = cidr.trim().split("/", 2);
            if (parts.length != 2) {
                return null;
            }
            InetAddress inet;
            try {
                inet = InetAddress.getByName(parts[0]);
            } catch (UnknownHostException e) {
                return null;
            }
            int prefix;
            try {
                prefix = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return null;
            }
            byte[] addressBytes = inet.getAddress();
            int totalBits = addressBytes.length * 8;
            if (prefix < 0 || prefix > totalBits) {
                return null;
            }
            byte[] mask = createMask(addressBytes.length, prefix);
            byte[] network = applyMask(addressBytes, mask);
            return new CidrBlock(network, mask);
        }

        private static byte[] createMask(int length, int prefixLength) {
            byte[] mask = new byte[length];
            int fullBytes = prefixLength / 8;
            int remainder = prefixLength % 8;
            for (int i = 0; i < fullBytes; i++) {
                mask[i] = (byte) 0xFF;
            }
            if (remainder > 0 && fullBytes < mask.length) {
                mask[fullBytes] = (byte) (0xFF << (8 - remainder));
            }
            return mask;
        }

        private static byte[] applyMask(byte[] address, byte[] mask) {
            byte[] result = new byte[address.length];
            for (int i = 0; i < address.length; i++) {
                result[i] = (byte) (address[i] & mask[i]);
            }
            return result;
        }

        boolean contains(InetAddress address) {
            byte[] addrBytes = address.getAddress();
            if (addrBytes.length != network.length) {
                return false;
            }
            for (int i = 0; i < network.length; i++) {
                if ((addrBytes[i] & mask[i]) != network[i]) {
                    return false;
                }
            }
            return true;
        }
    }
}


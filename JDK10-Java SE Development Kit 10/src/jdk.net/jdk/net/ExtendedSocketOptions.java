/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package jdk.net;

import java.io.FileDescriptor;
import java.net.SocketException;
import java.net.SocketOption;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.Set;
import jdk.internal.misc.JavaIOFileDescriptorAccess;
import jdk.internal.misc.SharedSecrets;

/**
 * Defines extended socket options, beyond those defined in
 * {@link java.net.StandardSocketOptions}. These options may be platform
 * specific.
 *
 * @since 1.8
 */
public final class ExtendedSocketOptions {

    private static class ExtSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;
        ExtSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }
        @Override public String name() { return name; }
        @Override public Class<T> type() { return type; }
        @Override public String toString() { return name; }
    }

    private ExtendedSocketOptions() { }

    /**
     * Service level properties. When a security manager is installed,
     * setting or getting this option requires a {@link NetworkPermission}
     * {@code ("setOption.SO_FLOW_SLA")} or {@code "getOption.SO_FLOW_SLA"}
     * respectively.
     */
    public static final SocketOption<SocketFlow> SO_FLOW_SLA = new
        ExtSocketOption<SocketFlow>("SO_FLOW_SLA", SocketFlow.class);

    /**
     * Disable Delayed Acknowledgements.
     *
     * <p>
     * This socket option can be used to reduce or disable delayed
     * acknowledgments (ACKs). When {@code TCP_QUICKACK} is enabled, ACKs are
     * sent immediately, rather than delayed if needed in accordance to normal
     * TCP operation. This option is not permanent, it only enables a switch to
     * or from {@code TCP_QUICKACK} mode. Subsequent operations of the TCP
     * protocol will once again disable/enable {@code TCP_QUICKACK} mode
     * depending on internal protocol processing and factors such as delayed ACK
     * timeouts occurring and data transfer, therefore this option needs to be
     * set with {@code setOption} after each operation of TCP on a given socket.
     *
     * <p>
     * The value of this socket option is a {@code Boolean} that represents
     * whether the option is enabled or disabled. The socket option is specific
     * to stream-oriented sockets using the TCP/IP protocol. The exact semantics
     * of this socket option are socket type and system dependent.
     *
     * @since 10
     */
    public static final SocketOption<Boolean> TCP_QUICKACK =
            new ExtSocketOption<Boolean>("TCP_QUICKACK", Boolean.class);

    private static final PlatformSocketOptions platformSocketOptions =
            PlatformSocketOptions.get();

    private static final boolean flowSupported =
            platformSocketOptions.flowSupported();
    private static final boolean quickAckSupported =
            platformSocketOptions.quickAckSupported();

    private static final Set<SocketOption<?>> extendedOptions = options();

    static Set<SocketOption<?>> options() {
        if (flowSupported) {
            if (quickAckSupported) {
                return Set.of(SO_FLOW_SLA, TCP_QUICKACK);
            } else {
                return Set.of(SO_FLOW_SLA);
            }
        } else if (quickAckSupported) {
            return Set.of(TCP_QUICKACK);
        } else {
            return Collections.<SocketOption<?>>emptySet();
        }
    }

    static {
        // Registers the extended socket options with the base module.
        sun.net.ext.ExtendedSocketOptions.register(
                new sun.net.ext.ExtendedSocketOptions(extendedOptions) {

            @Override
            public void setOption(FileDescriptor fd,
                                  SocketOption<?> option,
                                  Object value)
                throws SocketException
            {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkPermission(new NetworkPermission("setOption." + option.name()));

                if (fd == null || !fd.valid())
                    throw new SocketException("socket closed");

                if (option == SO_FLOW_SLA) {
                    assert flowSupported;
                    SocketFlow flow = checkValueType(value, option.type());
                    setFlowOption(fd, flow);
                } else if (option == TCP_QUICKACK) {
                    setQuickAckOption(fd, (boolean) value);
                } else {
                    throw new InternalError("Unexpected option " + option);
                }
            }

            @Override
            public Object getOption(FileDescriptor fd,
                                    SocketOption<?> option)
                throws SocketException
            {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkPermission(new NetworkPermission("getOption." + option.name()));

                if (fd == null || !fd.valid())
                    throw new SocketException("socket closed");

                if (option == SO_FLOW_SLA) {
                    assert flowSupported;
                    SocketFlow flow = SocketFlow.create();
                    getFlowOption(fd, flow);
                    return flow;
                } else if (option == TCP_QUICKACK) {
                    return getQuickAckOption(fd);
                } else {
                    throw new InternalError("Unexpected option " + option);
                }
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static <T> T checkValueType(Object value, Class<?> type) {
        if (!type.isAssignableFrom(value.getClass())) {
            String s = "Found: " + value.getClass() + ", Expected: " + type;
            throw new IllegalArgumentException(s);
        }
        return (T) value;
    }

    private static final JavaIOFileDescriptorAccess fdAccess =
            SharedSecrets.getJavaIOFileDescriptorAccess();

    private static void setFlowOption(FileDescriptor fd, SocketFlow f)
        throws SocketException
    {
        int status = platformSocketOptions.setFlowOption(fdAccess.get(fd),
                                                         f.priority(),
                                                         f.bandwidth());
        f.status(status);  // augment the given flow with the status
    }

    private static void getFlowOption(FileDescriptor fd, SocketFlow f)
            throws SocketException {
        int status = platformSocketOptions.getFlowOption(fdAccess.get(fd), f);
        f.status(status);  // augment the given flow with the status
    }

    private static void setQuickAckOption(FileDescriptor fd, boolean enable)
            throws SocketException {
        platformSocketOptions.setQuickAck(fdAccess.get(fd), enable);
    }

    private static Object getQuickAckOption(FileDescriptor fd)
            throws SocketException {
        return platformSocketOptions.getQuickAck(fdAccess.get(fd));
    }

    static class PlatformSocketOptions {

        protected PlatformSocketOptions() {}

        @SuppressWarnings("unchecked")
        private static PlatformSocketOptions newInstance(String cn) {
            Class<PlatformSocketOptions> c;
            try {
                c = (Class<PlatformSocketOptions>)Class.forName(cn);
                return c.getConstructor(new Class<?>[] { }).newInstance();
            } catch (ReflectiveOperationException x) {
                throw new AssertionError(x);
            }
        }

        private static PlatformSocketOptions create() {
            String osname = AccessController.doPrivileged(
                    new PrivilegedAction<String>() {
                        public String run() {
                            return System.getProperty("os.name");
                        }
                    });
            if ("SunOS".equals(osname)) {
                return newInstance("jdk.net.SolarisSocketOptions");
            } else if ("Linux".equals(osname)) {
                return newInstance("jdk.net.LinuxSocketOptions");
            } else {
                return new PlatformSocketOptions();
            }
        }

        private static final PlatformSocketOptions instance = create();

        static PlatformSocketOptions get() {
            return instance;
        }

        int setFlowOption(int fd, int priority, long bandwidth)
            throws SocketException
        {
            throw new UnsupportedOperationException("unsupported socket option");
        }

        int getFlowOption(int fd, SocketFlow f) throws SocketException {
            throw new UnsupportedOperationException("unsupported socket option");
        }

        boolean flowSupported() {
            return false;
        }

        void setQuickAck(int fd, boolean on) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_QUICKACK option");
        }

        boolean getQuickAck(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_QUICKACK option");
        }

        boolean quickAckSupported() {
            return false;
        }
    }
}

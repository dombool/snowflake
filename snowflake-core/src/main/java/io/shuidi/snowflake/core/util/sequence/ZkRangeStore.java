package io.shuidi.snowflake.core.util.sequence;

import com.codahale.metrics.Timer;
import io.shuidi.snowflake.core.report.ReporterHolder;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.utils.CloseableUtils;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static com.codahale.metrics.MetricRegistry.name;

/**
 * Author: Alvin Tian
 * Date: 2017/9/4 19:13
 */
public class ZkRangeStore implements RangeStore {

	private static final Logger LOGGER = LoggerFactory.getLogger(ZkRangeStore.class);

	private final InterProcessMutex lock;
	private String clientName;
	private CuratorFramework client;
	private long initialValue;
	private int rangeSize;
	private String sequencePath;
	private long time;
	private TimeUnit unit;

	public ZkRangeStore(String clientName, CuratorFramework client, String lockPath, String sequencePath, long time, TimeUnit unit,
	                    long initialValue, int rangeSize) {
		this.clientName = clientName;
		this.client = client;
		this.initialValue = initialValue;
		this.rangeSize = rangeSize;
		lockPath = ZKPaths.makePath(lockPath, clientName);
		this.sequencePath = ZKPaths.makePath(sequencePath, clientName);
		this.time = time;
		this.unit = unit;
		this.lock = new InterProcessMutex(client, lockPath);
	}

	private volatile long value;


	@Override
	public long getNextRange() throws InterruptedException {
		final Timer timer = ReporterHolder.metrics.timer(name("ZKRangeStore." + clientName, "getNextRange"));
		final Timer.Context context = timer.time();
		try {
			while (true) {
				try {
					if (lock.acquire(time, unit)) {
						break;
					}
				} catch (Exception e) {
					if (e instanceof InterruptedException) {
						throw (InterruptedException) e;
					} else {
						ReporterHolder.incException(e);
						LOGGER.error(clientName + "  lock.acquire error... ", e);
					}
				}
			}

			while (true) {
				try {
					client.sync()
					      .forPath(sequencePath);

					long value = Long.parseLong(new String(client.getData().forPath(sequencePath)));
					client.setData()
					      .forPath(sequencePath, String.valueOf(value + rangeSize).getBytes());
					this.value = value;
					LOGGER.info("" +
					            "getNextRange Client: {}, Value: {}", clientName, value);
					return value;
				} catch (Exception e) {
					if (e instanceof KeeperException.NoNodeException) {
						try {
							client.create()
							      .creatingParentsIfNeeded()
							      .withMode(CreateMode.PERSISTENT).forPath(sequencePath, String.valueOf(initialValue).getBytes());
						} catch (Exception e1) {
							LOGGER.error("opt value " + clientName, e);
							ReporterHolder.incException(e);
						}
					} else if (e instanceof InterruptedException) {
						throw (InterruptedException) e;
					} else {
						LOGGER.error("opt value " + clientName, e);
						ReporterHolder.incException(e);
					}
				}
			}
		} finally {
			try {
				context.stop();
				lock.release();
			} catch (Exception e) {
				if (e instanceof InterruptedException) {
					throw (InterruptedException) e;
				} else if (e instanceof IllegalMonitorStateException) {
				} else {
					LOGGER.warn("lock.release error " + clientName, e);
					ReporterHolder.incException(e);
				}
			}
		}
	}

	@Override
	public long getCurrRange() {
		return value;
	}

	@Override
	public String toString() {
		return clientName;
	}

	@Override
	public void close() {
		CloseableUtils.closeQuietly(client);
	}
}

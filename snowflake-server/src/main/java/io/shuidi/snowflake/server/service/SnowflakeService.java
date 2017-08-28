package io.shuidi.snowflake.server.service;

import com.google.common.collect.ImmutableSortedSet;
import io.shuidi.snowflake.core.service.impl.SnowflakeIDGenerator;
import io.shuidi.snowflake.server.enums.ErrorCode;
import io.shuidi.snowflake.server.server.SnowflakeServer;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Author: Alvin Tian
 * Date: 2017/8/28 20:15
 */
@Service
public class SnowflakeService implements InitializingBean {

	@Autowired
	SnowflakeIDGenerator snowflakeIDGenerator;

	@Autowired
	SnowflakeServer snowflakeServer;

	private static final Set<Integer> ALL_WORKER_IDS = ImmutableSortedSet.copyOf(Stream.iterate(0, a -> ++a).limit(1024).iterator());


	public Number generateId() {
		return snowflakeIDGenerator.generateId();
	}

	public int generateWorkId() {
		if (snowflakeServer.isLeader()) {

			return 1;
		} else {
			return ErrorCode.NOT_LEADER.getCode();
		}
	}


	@Override
	public void afterPropertiesSet() throws Exception {
		snowflakeServer.start();
	}

}

ARG HADOOP_VERSION=2.8.4
ARG HADOOP_WEBHDFS_PORT=50070
FROM abhioncbr/daflow-hadoop-base:${HADOOP_VERSION}

ENV HADOOP_WEBHDFS_PORT ${HADOOP_WEBHDFS_PORT}

ENV HDFS_CONF_dfs_namenode_name_dir=file:///hadoop/dfs/name
RUN mkdir -p /hadoop/dfs/name
VOLUME /hadoop/dfs/name

ADD run_nn.sh /run_nn.sh
RUN chmod a+x /run_nn.sh

CMD ["/run_nn.sh"]
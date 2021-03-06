package edu.bupt.wangfu.info.msg;

import java.io.Serializable;

public class Hello implements Serializable {
	private static final long serialVersionUID = 1L;

	public String startGroup;//本地集群名称
	public String endGroup;//对面的集群名称

	public String startBorderSwtId;//消息发起方边界的swtId
	public String endBorderSwtId;//本消息对标对面的边界swtId

	public String startOutPort;//记录这条消息是从哪个端口发出去的
	public String endOutPort;//记录这条消息对标对面的哪个端口

	public long reHelloPeriod;//判定节点失效的时间间隔
}

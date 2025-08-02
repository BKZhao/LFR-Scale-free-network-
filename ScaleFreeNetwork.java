package test1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import repast.simphony.context.Context;
import repast.simphony.context.space.graph.NetworkBuilder;
import repast.simphony.dataLoader.ContextBuilder;
import repast.simphony.engine.environment.RunEnvironment;
import repast.simphony.parameter.Parameters;
import repast.simphony.space.graph.Network;

public class ContextNetwork implements ContextBuilder<Object> {
    
    Context<Object> context;
    
    @Override
    public Context<Object> build(Context<Object> context) {
     
        // 创建网络
        NetworkBuilder<Object> netBuilder = new NetworkBuilder<Object>("Opinion Network", context, true);
        Network<Object> network = netBuilder.buildNetwork();
        
        
        Monitor monitor = new Monitor(context);
        context.add(monitor);

        // 使用平均度参数构建无标度网络
        buildScaleFreeNetwork(network, agents, averageDegree);
        
        // 根据节点度选择意见领袖
        selectOpinionLeaders(network, agents, opinionLeaderRatio);
        
        return context;
    }
    
    /**
     * 构建无标度网络 (Barabási-Albert模型) - 使用平均度参数
     */
    private void buildScaleFreeNetwork(Network<Object> network, List<Agent> agents, double averageDegree) {
        Random random = new Random();
        
        // 根据平均度计算参数
        // 在BA模型中，每个新节点连接m条边，最终平均度约为2m
        // 所以 m = averageDegree / 2
        int m = Math.max(1, (int) Math.round(averageDegree / 2.0));
        
        // 初始连通子图的节点数，通常设为m+1以确保连通性
        int m0 = Math.max(m + 1, 3); // 至少3个节点确保有足够的初始连接
        
        // 如果agent数量太少，调整参数
        if (agents.size() <= m0) {
            // 如果总节点数不足，创建一个完全图
            for (int i = 0; i < agents.size(); i++) {
                for (int j = i + 1; j < agents.size(); j++) {
                    network.addEdge(agents.get(i), agents.get(j));
                }
            }
            return;
        }
        
        // 步骤1: 创建初始完全连接的子图（m0个节点）
        for (int i = 0; i < m0; i++) {
            for (int j = i + 1; j < m0; j++) {
                network.addEdge(agents.get(i), agents.get(j));
            }
        }
        
        // 步骤2: 逐个添加剩余节点
        for (int i = m0; i < agents.size(); i++) {
            Agent newAgent = agents.get(i);
            
            // 计算所有已存在节点的度
            List<Agent> existingAgents = agents.subList(0, i);
            List<Double> probabilities = new ArrayList<>();
            double totalDegree = 0;
            
            for (Agent existingAgent : existingAgents) {
                int degree = network.getDegree(existingAgent);
                totalDegree += degree;
                probabilities.add((double) degree);
            }
            
            // 如果总度数为0（理论上不应该发生），均匀分布概率
            if (totalDegree == 0) {
                for (int j = 0; j < probabilities.size(); j++) {
                    probabilities.set(j, 1.0 / probabilities.size());
                }
            } else {
                // 转换为概率
                for (int j = 0; j < probabilities.size(); j++) {
                    probabilities.set(j, probabilities.get(j) / totalDegree);
                }
            }
            
            // 选择m个不同的节点进行连接（优先连接度数高的节点）
            List<Agent> selectedAgents = new ArrayList<>();
            for (int j = 0; j < Math.min(m, existingAgents.size()); j++) {
                Agent selectedAgent = selectByProbability(existingAgents, probabilities, random, selectedAgents);
                if (selectedAgent != null) {
                    selectedAgents.add(selectedAgent);
                    network.addEdge(newAgent, selectedAgent);
                }
            }
        }
        
        
        // 计算实际的边数和平均度
        int edgeCount = calculateEdgeCount(network, agents);
        double actualAverageDegree = (double) (2 * edgeCount) / agents.size();
   
        
        System.out.println("目标平均度: " + averageDegree);
        System.out.println("实际平均度: " + String.format("%.2f", actualAverageDegree));
        System.out.println("BA模型参数 - m0: " + m0 + ", m: " + m);
        
    }

    /**
     * 计算网络中的边数
     */
    private int calculateEdgeCount(Network<Object> network, List<Agent> agents) {
        int edgeCount = 0;
        for (Agent agent : agents) {
            edgeCount += network.getDegree(agent);
        }
        // 每条边被计算了两次（每个端点各一次），所以除以2
        return edgeCount / 2;
    }
    
    /**
     * 根据概率选择节点（优先连接算法）
     */
    private Agent selectByProbability(List<Agent> agents, List<Double> probabilities, 
                                    Random random, List<Agent> excludeList) {
        int maxAttempts = 100; // 防止无限循环
        int attempts = 0;
        
        while (attempts < maxAttempts) {
            double r = random.nextDouble();
            double cumulative = 0;
            
            for (int i = 0; i < agents.size(); i++) {
                cumulative += probabilities.get(i);
                if (r <= cumulative) {
                    Agent candidate = agents.get(i);
                    if (!excludeList.contains(candidate)) {
                        return candidate;
                    }
                    break;
                }
            }
            attempts++;
        }
        
        // 如果优先连接失败，随机选择一个未连接的节点
        for (Agent agent : agents) {
            if (!excludeList.contains(agent)) {
                return agent;
            }
        }
        return null;
    }
    
    /**
     * 根据节点度选择意见领袖
     */
    private void selectOpinionLeaders(Network<Object> network, List<Agent> agents, 
                                    double opRatio) {
        // 创建度数-agent对的列表
        List<DegreeAgentPair> degreeList = new ArrayList<>();
        for (Agent agent : agents) {
            int degree = network.getDegree(agent);
            degreeList.add(new DegreeAgentPair(agent, degree));
        }
        
        // 按度数降序排列
        Collections.sort(degreeList, new Comparator<DegreeAgentPair>() {
            @Override
            public int compare(DegreeAgentPair o1, DegreeAgentPair o2) {
                return Integer.compare(o2.degree, o1.degree); // 降序
            }
        });
        
        // 计算意见领袖数量
        int opinionLeaderCount = (int) (agents.size() * opRatio);

        
        // 设置意见领袖（度数最高的一批）
        for (int i = 0; i < opinionLeaderCount && i < degreeList.size(); i++) {
            Agent agent = degreeList.get(i).agent;
            agent.setOpinionLeader(true);
            agent.setState(1);
        }
        


        int edgeCount = calculateEdgeCount(network, agents);
        
        System.out.println("网络构建完成:");
        System.out.println("总节点数: " + agents.size());
        System.out.println("总边数: " + edgeCount);
        System.out.println("意见领袖数: " + opinionLeaderCount);
    }
    
    /**
     * 内部类：用于存储度数和对应的agent
     */
    private static class DegreeAgentPair {
        Agent agent;
        int degree;
        
        public DegreeAgentPair(Agent agent, int degree) {
            this.agent = agent;
            this.degree = degree;
        }
    }
}
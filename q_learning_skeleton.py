import numpy as np
import random


NUM_EPISODES = 1000
MAX_EPISODE_LENGTH = 500

DEFAULT_DISCOUNT = 0.9
EPSILON = 0.05
LEARNINGRATE = 0.1


class QLearner():
    """
    Q-learning agent
    """
    def __init__(self, num_states, num_actions, discount=DEFAULT_DISCOUNT, learning_rate=LEARNINGRATE, epsilon = EPSILON): # You can add more arguments if you want
        self.name = "agent1"
        self.num_actions = num_actions
        self.num_states = num_states
        
        self.alpha      = learning_rate
        self.gamma      = discount
        self.epsilon    = epsilon

        self.Q = np.zeros((num_states, num_actions))
        self.Na= np.ones((num_states, num_actions))
    

    def process_experience(self, state, action, next_state, reward, done): # You can add more arguments if you want
        """
        Update the Q-value based on the state, action, next state and reward.
        """
        if done:
            self.Q[state,action] = (1-self.alpha)*self.Q[state,action] + self.alpha*reward
        else:
            self.Q[state,action] = (1-self.alpha)*self.Q[state,action] + self.alpha*(reward + self.gamma * np.max(self.Q[next_state,:]))
        self.Na[state,action]+=1

    def select_action(self, state): # You can add more arguments if you want
        """
        Returns an action, selected based on the current state
        """

        # if random.uniform(0,1) < self.epsilon:
        #     """
        #     Exploring random actions
        #     """
            
        #     return random.randint(0,(self.num_actions-1))
        
        # else:
        #     """
        #     Exploiting a known action
        #     Returns the action with the highest Q value
        #     If multiple actions have the same highest Q value,
        #     then a random action will be chosen among them
        #     """
        c=0.1
        
        N = np.sum(self.Na[state,:]) #number of times the state was visited
        ucb_vector = self.Q[state,:]
        
        for i in range (0,self.num_actions):
            extra_term =  c*(np.sqrt(np.log(N)/self.Na[state,i]))
            ucb_vector[i]=self.Q[state,i] + extra_term
        
        print (ucb_vector)
        Q_max = np.argwhere(ucb_vector == np.max(ucb_vector)) 
        
        return random.choice(Q_max).item()  
                           


    def report(self):
        """
        Function to print useful information, printed during the main loop
        """
        print(self.Q)

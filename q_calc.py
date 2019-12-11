import pandas as pd

#a = [left, right]
#s =  location in alley, goal excluded

DISCOUNT_VALUE=0.95

q_dataframe = pd.DataFrame(0.0, index=range(12), columns=range(2))

R = pd.DataFrame(0.0, index=range(12), columns=range(2))
R.at[3, 1] = -1.0
R.at[5, 0] = -1.0
R.at[7, 1] = -1.0
R.at[9, 0] = -1.0
R.at[11, 1] = 10.0

print(R)

def getprob(s,a,s_prime):
    # returns P(s'|s,a)

    # walk left/right
    if s-s_prime==1 and a==0:
        return 1
    if s-s_prime==-1 and a==1:
        return 1

    # default alternative, i.e. is impossible transitions
    return 0


def update_iteration(q_dataframe):
    new_q = pd.DataFrame(0.0, index=range(12), columns=range(2))
    for action in range(2):
        for state in range(12):

            annoying_sum = 0
            for other_state in range(12):

                max_other_action = 0
                for other_action in range(2):
                    max_other_action = max(max_other_action, q_dataframe.iloc[other_state, other_action])

                annoying_sum += getprob(state, action, other_state)*max_other_action
            # print(annoying_sum)
            new_q.at[state, action] = R.iloc[state,action] + DISCOUNT_VALUE*annoying_sum
    return new_q


difference = 100
while difference > 0.01:
    new_q_dataframe = update_iteration(q_dataframe)

    difference = new_q_dataframe.subtract(q_dataframe).values.sum()
    # print(difference)
    # print(new_q_dataframe)
    # print(q_dataframe)
    q_dataframe = new_q_dataframe

print(q_dataframe)

// // C++ program to find minimum number
// // of operations to convert s1 to s2
// #include <iostream>
// #include <vector>
// #include <algorithm>
// using namespace std;

// int editDistance(string &s1, string &s2) {
  
//     int m = s1.length();
//     int n = s2.length();

//     // Create a table to store results of subproblems
//     vector<vector<int>> dp(m + 1, vector<int>(n + 1));

//     // Fill the known entries in dp[][]
//     // If one string is empty, then answer 
//     // is length of the other string
//     for (int i = 0; i <= m; i++) 
//         dp[i][0] = i;
//     for (int j = 0; j <= n; j++) 
//         dp[0][j] = j; 

//     // Fill the rest of dp[][]
//     for (int i = 1; i <= m; i++) {
//         for (int j = 1; j <= n; j++) {
//             if (s1[i - 1] == s2[j - 1])
//                 dp[i][j] = dp[i - 1][j - 1];
//             else
//                 dp[i][j] = 1 + min({dp[i][j - 1],  
//                                  dp[i - 1][j],   
//                                  dp[i - 1][j - 1]});
//         }
//     }

//     return dp[m][n];
// }

// int main() {
//     string s1,s2;
//     cin >> s1 >> s2;

//     cout << editDistance(s1, s2);

//     return 0;
// }


// A Naive recursive C++ program to find minimum number
// of operations to convert s1 to s2
#include <iostream>
#include <string>
#include <algorithm>
using namespace std;

// Recursive function to find number of operations 
// needed to convert s1 into s2.
int editDistRec(string& s1, string& s2, int m, int n) {
  
    // If first string is empty, the only option is to
    // insert all characters of second string into first
    if (m == 0) return n;

    // If second string is empty, the only option is to
    // remove all characters of first string
    if (n == 0) return m;

    // If last characters of two strings are same, nothing
    // much to do. Get the count for
    // remaining strings.
    if (s1[m - 1] == s2[n - 1]) 
      return editDistRec(s1, s2, m - 1, n - 1);

    // If last characters are not same, consider all three
    // operations on last character of first string,
    // recursively compute minimum cost for all three
    // operations and take minimum of three values.
    return 1 + min({editDistRec(s1, s2, m, n - 1),  
                    editDistRec(s1, s2, m - 1, n),   
                    editDistRec(s1, s2, m - 1, n - 1)}); 
}

// Wrapper function to initiate the recursive calculation
int editDistance(string& s1, string& s2) {
    return editDistRec(s1, s2, s1.length(), s2.length());
}

int main() {
    
    string s1, s2;
    cin >> s1 >> s2;

    cout << editDistance(s1, s2);

    return 0;
}
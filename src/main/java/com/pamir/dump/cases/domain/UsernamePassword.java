package com.pamir.dump.cases.domain;

public class UsernamePassword {

   private String username;
   private String password;

   public UsernamePassword(String username, String password) {
      this.username = username;
      this.password = password;
   }

   public String getUsername() {
      return this.username;
   }

   public String getPassword() {
      return this.password;
   }

   public void setUsername(String username) {
      this.username = username;
   }

   public void setPassword(String password) {
      this.password = password;
   }

}
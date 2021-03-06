package com.javaRealtor.app;

import java.net.URLEncoder;
import com.mashape.unirest.http.*;
import java.io.File;
import java.io.FileWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import org.json.*;
import java.util.ArrayList;
import java.util.Scanner;


public class App {
  public static void main( String[] args ) throws Exception {
    Scanner scan = new Scanner(System.in);
    ArrayList<Integer> zipCodes = new ArrayList<Integer>();//user-input zip codes
    ArrayList<HttpResponse<String>> zipLists = new ArrayList<HttpResponse<String>>();//arrayList of lists by zip code
    ArrayList<String> propertyIDs = new ArrayList<String>();//list of all property IDs to be used today
    ArrayList<HttpResponse<String>> propertyDetails = new ArrayList<HttpResponse<String>>(); //arrayList of property details
    ArrayList<String> finalPropDetails = new ArrayList<String>(); //arrayList of csv entries
    
    //pull in previously used IDs
    ArrayList<String> blacklist = new ArrayList<String>();
    try {
      File prevProps = new File("PROPS.txt");
      Scanner myReader = new Scanner(prevProps);
      while (myReader.hasNextLine()) {
        String usedPropID = myReader.nextLine();
        blacklist.add(usedPropID);
      }
      myReader.close();
    } catch (FileNotFoundException e) {
      System.out.println("An error occurred. The requested file wasn't found.");
      e.printStackTrace();
    }
    
    
    int numOfReqs = 0;
    
    //get all parameters for search
    System.out.println( "ENTER DATA:\nZIP CODES (Enter 0 when complete)" );
    int input = scan.nextInt();
    
    while(input != 0) {
      zipCodes.add(input);
      input = scan.nextInt();
    }
    
    System.out.println("ENTER MAX PRICE");
    int maxPrice = scan.nextInt();
    System.out.println("ENTER NUMBER OF LISTINGS");
    int numListings = scan.nextInt();
    System.out.println("Working...");
    
    String listBaseURL = 
      "https://realtor.p.rapidapi.com/properties/v2/list-for-sale?beds_min=3&sort=price_reduced_date&is_pending=false&is_contingent=false&offset=0";
    //get lists of properties for each zip code
    for (int zip : zipCodes) {
      zipLists.add(Unirest.get(listBaseURL + "&postal_code=" + zip + "&price_max=" + maxPrice + "&limit=" + numListings)
                     .header("x-rapidapi-host", "realtor.p.rapidapi.com")
                     .header("x-rapidapi-key", "whoa there I'm not putting my key here!") //TODO: add api key
                     .asString()
                  );
      numOfReqs++;
      System.out.println("Zip Code List added");
    }
    
    //now get the Realtor.com Property ID for each of the aforementioned properties (200 per zip code)
    for (HttpResponse<String> zipList : zipLists) {
      String jsonString = zipList.getBody();
      JSONObject obj = new JSONObject(jsonString);
      System.out.println("ZipArray Length: " + obj.getJSONArray("properties").length());
      for (int i = 0; i < obj.getJSONArray("properties").length(); i++) {
        //this is a long one; we're getting the i'th listing and then getting the string "property_id" from it, as long as it's not been done before
        if (blacklist.indexOf(obj.getJSONArray("properties").getJSONObject(i).getString("property_id")) == -1) {
          propertyIDs.add(obj.getJSONArray("properties").getJSONObject(i).getString("property_id"));
        }
      }
      System.out.println("\nOne Zip Code List Parsed");
      System.out.println("Number of Property IDs: " + propertyIDs.size());
    }
    
    //now propertyIDs should have about 200*the number of zip codes specified of Property IDs
    int c = 0;
    System.out.println("Number of Property IDs: " + propertyIDs.size());
    for (String propertyID : propertyIDs) {
      c++;
      propertyDetails.add(
                          Unirest.get("https://realtor.p.rapidapi.com/properties/v2/detail?property_id=" + propertyID)
                            .header("x-rapidapi-host", "realtor.p.rapidapi.com")
                            .header("x-rapidapi-key", "whoa there I'm not putting my key here!")
                            .asString()
                         );
      numOfReqs++;
      if (c % 50 == 0) {System.out.println(c + " property details recieved");}
    }
    System.out.println("All Property IDs Parsed");
    
    //write raw JSON now, so that we can at least use it later
    try {
      FileWriter myWriter = new FileWriter("rawJSON.txt", true);
      for (HttpResponse<String> propertyDetail : propertyDetails) {
        String jsonString = propertyDetail.getBody();
        myWriter.write(jsonString);
        myWriter.write("\n");
      }
      myWriter.close();
      System.out.println("wrote raw JSON strings");
    } catch (IOException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
    
    
    //now propertyDetails should have as many HttpResponse<String> as there are entries in propertyIDs. This next loop will then run through each one (200n).
    c = 0;
    for (HttpResponse<String> propertyDetail : propertyDetails) {
      
      String jsonString = propertyDetail.getBody();
      JSONObject obj = new JSONObject(jsonString);
      int step = -2;
      String propCSV = "";
      try {
        propCSV += (obj.getJSONArray("properties").getJSONObject(0).getString("property_id") + ",");
        step++;
        JSONArray features = obj.getJSONArray("properties").getJSONObject(0).getJSONArray("features");
        step++;
        boolean flag = false;
        for (int i = 0; i < features.length(); i++) {
          if (features.getJSONObject(i).getString("category").equals("School Information")) {
            if (features.getJSONObject(i).getJSONArray("text").getString(3).equals("School District: Round Rock ISD")||
                features.getJSONObject(i).getJSONArray("text").getString(3).equals("School District: Leander ISD")) {
              flag = true;
              propCSV += features.getJSONObject(i).getJSONArray("text").getString(0) + ",";
              propCSV += features.getJSONObject(i).getJSONArray("text").getString(1) + ",";
              propCSV += features.getJSONObject(i).getJSONArray("text").getString(2) + ",";
              break;
            }
          }
        }
        if (flag) {
          propCSV += (obj.getJSONArray("properties").getJSONObject(0).getInt("beds") + ",");
          step++;
          propCSV += obj.getJSONArray("properties").getJSONObject(0).getInt("year_built") + ",";
          step++;
          propCSV += obj.getJSONArray("properties").getJSONObject(0).getJSONObject("lot_size").getInt("size") + ",";
          step++;
          propCSV += obj.getJSONArray("properties").getJSONObject(0).getJSONObject("building_size").getInt("size") + ",";
          step++;
          propCSV += obj.getJSONArray("properties").getJSONObject(0).getInt("price") + ",";
          step++;
          int baths = 0;
          try {baths += obj.getJSONArray("properties").getJSONObject(0).getInt("baths_full");} catch (JSONException j) {}
          try {baths += obj.getJSONArray("properties").getJSONObject(0).getInt("baths_half");} catch (JSONException j) {}
          propCSV += baths + ",";
          step++;
          propCSV += obj.getJSONArray("properties").getJSONObject(0).getJSONObject("address").getString("line") + " "
            + obj.getJSONArray("properties").getJSONObject(0).getJSONObject("address").getString("city") + " "
            + obj.getJSONArray("properties").getJSONObject(0).getJSONObject("address").getString("state_code") + " "
            + obj.getJSONArray("properties").getJSONObject(0).getJSONObject("address").getString("postal_code") + ",";
          step++;
          propCSV += obj.getJSONArray("properties").getJSONObject(0).getString("rdc_web_url");
        }
        
      }
      
      catch (JSONException j) {
        System.out.println("Exception at ID " + propertyIDs.get(c) + ". Steps completed:" + step);
      }
      finalPropDetails.add(propCSV);
      if (c % 50 == 0) {System.out.println(c + "details converted to CSV");}
      c++;
    }
    
    //write propIDs
    try {
      FileWriter myWriter = new FileWriter("PROPS.txt", true);
      for (String propID : propertyIDs) {
        myWriter.write(propID);
        myWriter.write("\n");
      }
      myWriter.close();
      System.out.println("updated list of used property IDs");
    } catch (IOException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
    
    //write CSV data
    try {
      FileWriter myWriter = new FileWriter("dataCSV.txt", true);
      for (String propDetail : finalPropDetails) {
        myWriter.write(propDetail);
        myWriter.write("\n");
      }
      myWriter.close();
      System.out.println("Successfully wrote to the csv file. Program completed with " + numOfReqs + " requests.\nData format is: Schools (EHM), Beds, Year, Lot, Building, Price, Baths, Address");
    } catch (IOException e) {
      System.out.println("An error occurred.");
      e.printStackTrace();
    }
  }
}

package com.uniuni.SysMgrTool.thirdpart;

import static java.net.URLEncoder.encode;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ThirdApi {
    public boolean parseAddress(String address , Double[] coordinate) throws IOException {

        if (coordinate == null || coordinate.length < 2)
            return false;

        address = encode(address);
        // Build the URL for the API request
        String urlString =
                "https://international-street.api.smarty.com/verify?key=21102174564513388&agent=smarty+(website:demo%2Fsingle-address%40latest)&match=enhanced&candidates=5&geocode=true&country=CAN&freeform="
                        + address;

        // Create a URL object
        URL url = new URL(urlString);
        // Open a connection
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        // Set the request method
        connection.setRequestMethod("GET");

        // Set request headers
        Map<String, String> headers = new HashMap<>();
        headers.put("authority", "international-street.api.smarty.com");
        headers.put("method", "GET");
        headers.put("path", "/verify?key=21102174564513388&agent=smarty+(website:demo%2Fsingle-address%40latest)&match=enhanced&candidates=5&geocode=true&country=CAN&freeform=314Princess+Margaret+Blvd+Dartmouth+NS");
        headers.put("scheme", "https");
        headers.put("accept", "application/json, text/plain, */*");
        headers.put("accept-encoding", "gzip, deflate, br");
        headers.put("accept-language", "en,zh-CN;q=0.9,zh-TW;q=0.8,zh;q=0.7");
        headers.put("origin", "https://www.smarty.com");
        headers.put("referer", "https://www.smarty.com/");
        headers.put("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"Windows\"");
        headers.put("sec-fetch-dest", "empty");
        headers.put("sec-fetch-mode", "cors");
        headers.put("sec-fetch-site", "same-site");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            connection.setRequestProperty(entry.getKey(), entry.getValue());
        }

        // Set user-agent
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Set cookies
        connection.setRequestProperty("Cookie", "_vwo_uuid_v2=DCA62B0519F8A4263176715CE5D869C16|c08ce33199575925b6f3182c88f8d398; _vis_opt_s=1%7C; _vis_opt_test_cookie=1; _vwo_uuid=DCA62B0519F8A4263176715CE5D869C16; _vis_opt_exp_78_combi=1; _vwo_ds=3%3Aa_0%2Ct_0%3A0%241703549751%3A95.49221382%3A%3A%3A3_0%2C2_0%3A0; _gcl_au=1.1.1076037887.1703549755; _ga=GA1.1.804556431.1703549755; _clck=irv5qn%7C2%7Cfhv%7C0%7C1455; FPID=FPID2.2.e4RjKHZx6v9Q2LI%2FxbcMnOJc9oIGwyYMxdrmmY9v8Tw%3D.1703549755; FPAU=1.1.1076037887.1703549755; _gtmeec=e30%3D; FPLC=bbKfOXdAk028ZNT0ptzx4lvt1IedGDZoPDVWQF6HpqCM7%2FvJOGWUjMBO12u4AskZujBz5fmS7qtzH%2FGke9%2F%2BZik%2Fbmzyqo4rDH6aXj%2B%2FkKZnV2jqTBwPX7inoXlOyQ%3D%3D; __hstc=152446182.9f56b40fb282cb40e69dd699ddac0b04.1703549758008.1703549758008.1703549758008.1; hubspotutk=9f56b40fb282cb40e69dd699ddac0b04; __hssrc=1; _uetsid=ef6fea20a38311ee954f8340f002c6d2; _uetvid=ef700660a38311eebef1776c7fceed20; _vwo_sn=6607; _clsk=iwacxy%7C1703557492348%7C2%7C1%7Cx.clarity.ms%2Fcollect; _ga_822X07YMEL=GS1.1.1703557673.2.0.1703557673.0.0.0");

        // Get the response code
        int responseCode = connection.getResponseCode();
        System.out.println("Response Code: " + responseCode);

        // Read the response
        // Read the response
        BufferedReader in;

        if ("gzip".equals(connection.getContentEncoding())) {
            in = new BufferedReader(new InputStreamReader(new GZIPInputStream(connection.getInputStream())));
        } else {
            in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        }

        String inputLine;
        StringBuilder response = new StringBuilder();

        while ((inputLine = in.readLine()) != null) {
            response.append(inputLine);
        }

        in.close();

        // Print the response
        String s = response.toString();
        System.out.println(s);
        Gson gson = new Gson();

        String postalCode = "";
        AddressParseResponse[] rsps = gson.fromJson(response.toString(), AddressParseResponse[].class);
        if (rsps == null) {
            System.out.println("Can't get the Postal Code of address:" + address);
        } else {
            AddressParseResponse rsp = rsps[0];
            Metadata meta = rsp.getMetadata();
            if (meta != null)
            {
                coordinate[1] = meta.getLongitude();
                coordinate[0] = meta.getLatitude();

                return true;
            }
            //"Description":"Bedford, NS, B4B 0Z9"}
            //System.out.println(postalCode);
        }
        return false;
    }
}

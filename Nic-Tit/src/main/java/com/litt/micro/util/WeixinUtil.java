package com.litt.micro.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.methods.multipart.StringPart;
import org.apache.commons.httpclient.protocol.Protocol;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import com.litt.micro.util.message.Token;
import com.litt.micro.util.properties.PropertiesReadUtils;

import net.sf.json.JSONObject;

public class WeixinUtil extends HttpServlet{
	
	//读取配置文件获取appid和AppSecret
		private static final String APPID = PropertiesReadUtils.getWechatString("appid");
		private static final String APPSECRET = PropertiesReadUtils.getWechatString("AppSecret");
		//微信OAuth2.0授权
		private static final String OAuth2="https://open.weixin.qq.com/connect/oauth2/authorize?appid=APPID&redirect_uri=REDIRECT_URL&response_type=code&scope=snsapi_base#wechat_redirect";
		//获取授权code
		private static final String CodeUrl="https://api.weixin.qq.com/sns/oauth2/access_token?appid=APPID&secret=APPSECRET&code=CODE&grant_type=authorization_code";
		//获取access_token
		private static final String ACCESS_TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APPID&secret=APPSECRET";
		// 新增临时素材,媒体文件在微信后台保存时间为3天，即3天后media_id失效。
		private static final String UPLOAD_URL = "https://api.weixin.qq.com/cgi-bin/media/upload?access_token=ACCESS_TOKEN&type=TYPE";

		private static final String CREATE_MENU_URL = "https://api.weixin.qq.com/cgi-bin/menu/create?access_token=ACCESS_TOKEN";

		private static final String QUERY_MENU_URL = "https://api.weixin.qq.com/cgi-bin/menu/get?access_token=ACCESS_TOKEN";

		private static final String DELETE_MENU_URL = "https://api.weixin.qq.com/cgi-bin/menu/delete?access_token=ACCESS_TOKEN";

		/**
		 * 获取js-ticket
		 */
		private static final String REQUEST_URL = "https://api.weixin.qq.com/cgi-bin/ticket/getticket?access_token=ACCESS_TOKEN&type=jsapi";
		/**
		 * 下载多媒体接口
		 */
		
		private static final String DOWNLOAD_URL = "http://api.weixin.qq.com/cgi-bin/media/get?access_token=ACCESS_TOKEN&media_id=MEDIA_ID";
		
		
		//得到appid
		public static String getAppid() {
			return APPID;
		}
		
		//用户授权
		public static String getOAuth2(){
			String OAuth2Url=OAuth2.replace("APPID", APPID);
			System.out.println("授权连接--->"+OAuth2Url);
			return OAuth2Url;
		}
			
		/**
		 * GET方式
		 * @param url
		 */
		public static JSONObject doGetStr(String url) throws ParseException,IOException {
			DefaultHttpClient client = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet(url);
			JSONObject jsonObject = null;
			HttpResponse httpResponse = client.execute(httpGet);
			HttpEntity entity = httpResponse.getEntity();
			if (entity != null) {
				String result = EntityUtils.toString(entity, "UTF-8");
				System.out.println("这是使用get方式请求得到的result："+result);
				
				jsonObject = JSONObject.fromObject(result);
			}
			return jsonObject;
		}

		/**
		 * POST方式
		 * @param url
		 * @param outStr
		 */
		public static JSONObject doPostStr(String url, String outStr)
				throws ParseException, IOException {
			DefaultHttpClient client = new DefaultHttpClient();
			HttpPost httpost = new HttpPost(url);
			JSONObject jsonObject = null;
			httpost.setEntity(new StringEntity(outStr, "UTF-8"));
			HttpResponse response = client.execute(httpost);
			String result = EntityUtils.toString(response.getEntity(), "UTF-8");
			jsonObject = JSONObject.fromObject(result);
			return jsonObject;
		}
		
		
		
		/**
		 * 获取accessToken
		 * 将获取的access_token放到properties文件里面，及时保存和更新
		 */
		public static Token getAccessToken() throws  IOException {
			Token token = new Token();
		     //保存access_token文件名字  
            String FileName = "wechat.properties";  
            try {  
                // 从文件中获取token值及时间  
                Properties prop = new Properties();// 属性集合对象  
                 //获取文件流  
                InputStream fis = WeixinUtil.class.getClassLoader().getResourceAsStream(FileName);  
                prop.load(fis);// 将属性文件流装载到Properties对象中  
                fis.close();// 关闭流  
                //获取Appid，APPsecret  
                String APPID = prop.getProperty("appid");  
                String APPSECRET = prop.getProperty("AppSecret");  
                // 获取accesstoken，初始值为空，第一次调用之后会把值写入文件  
                String access_token = prop.getProperty("access_token");  
                String expires_in = prop.getProperty("expires_in");  
                String last_time = prop.getProperty("last_time");  
                
                int int_expires_in = 0;  
                long long_last_time = 0;  
      
                try {  
                    int_expires_in = Integer.parseInt(expires_in);  
                    long_last_time = Long.parseLong(last_time);  
      
                } catch (Exception e) {  
                	
                }  
                //得到当前时间  
                long current_time = System.currentTimeMillis();  
                // 每次调用，判断expires_in是否过期，如果token时间超时，重新获取，expires_in有效期为7200  
                if ((current_time - long_last_time) / 1000 >= int_expires_in) {  
                    //获取token url  
                    String url = "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid="  
                            + APPID + "&secret=" + APPSECRET;  
                    //发送http请求得到json流  
                    JSONObject jobject = doGetStr(url);  
                    //从json流中获取access_token  
                    
                    String j_access_token = null;
					String j_expires_in = null;
					try {
						j_access_token = (String) jobject.get("access_token");  
						j_expires_in = String.valueOf(jobject.get("expires_in"));
					} catch (Exception e) {
						e.printStackTrace();
					}  
                    //保存access_token  
                    if (j_access_token != null && j_expires_in != null) {  
                        prop.setProperty("access_token", j_access_token);  
                        prop.setProperty("expires_in", j_expires_in);  
                        prop.setProperty("last_time", System.currentTimeMillis() + "");  
      
                        URL url_ = WeixinUtil.class.getClassLoader().getResource(FileName);  
                        FileOutputStream fos = new FileOutputStream(new File(url_.toURI()));  
                        prop.store(fos, null);  
                        fos.close();// 关闭流  
                    }  
                    //如果已经过期返回获取到的access_token  
                      token.setAccessToken(j_access_token);  
                      return token;
                } else {  
                    //如果没有过期，返回从文件中读取的access_token  
                	token.setAccessToken(access_token);  
                    return token;  
                }  
            } catch (Exception e) {  
            	
                return null;  
            }  
		}

		/**
		 * 获取jsapi_ticket
		 * @throws IOException
		 * @throws ParseException
		 */
		public static String getTickect(String access_token) throws ParseException,IOException {
			String ticket = "";
			String url = REQUEST_URL.replace("ACCESS_TOKEN", access_token);
			// 获取网页授权凭证
			JSONObject jsonObject = doGetStr(url);
			if (jsonObject != null) {
				try {
					ticket = jsonObject.getString("ticket");
				} catch (Exception e) {

					int errorCode = jsonObject.getInt("errcode");
					String errorMsg = jsonObject.getString("errmsg");
					System.out.println("获取jsapi_ticket失败 ，返回信息是errcode:" + errorCode+ "," + "errmsg:" + errorMsg);
				}
			}
			return ticket;
		}

		// 获得签名js signature
		public static String getSignature(String jsapi_ticket, String timestamp,String nonce, String jsurl) throws IOException {
			/****
			 * 对 jsapi_ticket、 timestamp 和 nonce 按字典排序 对所有待签名参数按照字段名的 ASCII
			 * 码从小到大排序（字典序）后，使用 URL 键值对的格式（即key1=value1&key2=value2…）拼接成字符串
			 * string1。这里需要注意的是所有参数名均为小写字符。 接下来对 string1 作 sha1 加密，字段名和字段值都采用原始值，不进行
			 * URL 转义。即 signature=sha1(string1)。
			 * **如果没有按照生成的key1=value&key2=value拼接的话会报错
			 */
			String[] paramArr = new String[] { "jsapi_ticket=" + jsapi_ticket,
					"timestamp=" + timestamp, "noncestr=" + nonce, "url=" + jsurl };
			Arrays.sort(paramArr);
			// 将排序后的结果拼接成一个字符串
			String content = paramArr[0].concat("&" + paramArr[1]).concat("&" + paramArr[2]).concat("&" + paramArr[3]);
			System.out.println("拼接之后的sign为:" + content);
			String gensignature = null;
			try {
				MessageDigest md = MessageDigest.getInstance("SHA-1");
				// 对拼接后的字符串进行 sha1 加密
				byte[] digest = md.digest(content.toString().getBytes());
				gensignature = SignUtil.byteToStr(digest);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
			}
			// 将 sha1 加密后的字符串与 signature 进行对比
			if (gensignature != null) {
				return gensignature;// 返回signature
			} else {
				return "false";
			}
		}
		
		//通过code获取用户openid的值 
		public static String getOpenid(String code){
			String url=CodeUrl.replace("APPID", APPID).replace("APPSECRET", APPSECRET).replace("CODE",code);
			System.out.println("授权连接code--->"+url);
			String openId="";
			try {
				URL getUrl=new URL(url);	//表示Internet上某一资源的地址
				HttpURLConnection http=(HttpURLConnection)getUrl.openConnection();//使用HttpURLConnection类，来访问HTTP协议的网络资源，HttpURLConnection对象不能直接构造，需要通过URL类中的openConnection方法类获得
				http.setRequestMethod("GET"); //设置请求方式
				http.setRequestProperty("Content-Type","application/x-www-form-urlencoded");//设置使用标准编码格式编码参数的名-值对
				http.setDoOutput(true);            // 设置是否向HttpURLConnection输出
				http.setDoInput(true);            // 设置是否从httpUrlConnection读入
		
				http.connect();            // 连接
				InputStream is = http.getInputStream(); 
				int size = is.available(); 
				byte[] b = new byte[size];
				is.read(b);
				String message = new String(b, "UTF-8");
				JSONObject json = JSONObject.fromObject(message);
				openId = json.getString("openid");
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			System.out.println("此时的openid="+openId);
			return openId;
		 	
		}
		/**
		 * 微信服务器素材上传（图片）
		 * @param filePath图片路径
		 * @return media_id 图文所需要的图片id
		 * @throws ParseException
		 * @throws IOException
		 */
		
		
		/**
		 * 微信服务器素材上传
		 * @param file表单名称media
		 * @param token access_token
		 * @param type type只支持四种类型素材(video/image/voice/thumb)
		 *            
		 */
		
		/**
		 * 获取临时素材（微信服务器）
		 * @param mediaId媒体文件id，即微信服务器id
		 * @throws IOException
		 * @throws ParseException
		 */
		

		
		/**
		 * 保存图片至服务器
		 * @param mediaId
		 * @return 文件名
		 * @throws IOException
		 * @throws ParseException
		 */
		
	
		
		
		
		
		
		
}

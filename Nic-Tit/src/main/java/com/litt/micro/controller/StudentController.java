package com.litt.micro.controller;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.util.TextUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import com.alibaba.fastjson.JSONObject;
import com.litt.micro.entity.Student;
import com.litt.micro.service.IStudentService;
import com.litt.micro.util.SignUtil;
import com.litt.micro.util.WeixinUtil;
import com.litt.micro.util.AES.AES;
import com.litt.micro.util.check.Checkout;
import com.litt.micro.util.post.PostWithJson;
import com.litt.micro.util.prompt.HintoFront;
import com.litt.micro.util.stu.MicroStu;
import com.litt.micro.util.stu.Rdata;


/**
 * 验证高校学生信息
 * @author Lenovo
 *
 */
@Controller
@RequestMapping("/student")
public class StudentController {
	final static String app_key="2C2F5BDF63543494";
	final static String app_secret="C4960C80E48676D26FDB57AA684E9070";
	
	@Autowired
	private IStudentService studentServiceImpl;
	
	/**
	 * 
	 * ram timestamp
	 * @param nonce_str
	 * @param sign
	 */
	@RequestMapping("/sign")
	public String  load(HttpServletRequest request,HttpServletResponse response, String state ,String app_key ,
			String timestamp,String nonce_str,String sign,String code ){
			System.out.println(state+"这是state代码，是不是会变的");
			if (SignUtil.checkSignature(state,app_key, timestamp, nonce_str,sign)) {
				//跳转到登录页面,将state放心request提供给下个方法
				request.getSession().setAttribute("state", state);	
				return "/jsp/openId/openId";
			} else {
				return "/jsp/error/error";
			}
		}
	
	
	/**
	 * 获取用户的基本信息openId
	 * @param code
	 * @return 
	 */
	@RequestMapping("information")
	public String getUserInfo(HttpServletRequest request,String code){
		System.out.println("这是获取到的code:"+code);
		//将code放到session域中
		request.getSession().setAttribute("code", code);
		return "/jsp/student/stu_identify";
	}
	
	
	/**
	 * 用户信息进行验证，同时将微信用户的openid和student表进行关联
	 * @param request
	 * @param response
	 * @param name
	 * @param number
	 * @param telephone
	 * @param ID
	 * @param state
	 * @return
	 */
	@RequestMapping("/identify")
	public String  stuIdentify(HttpServletRequest request,HttpServletResponse response,
			String name,String number,String telephone,String ID,String state
			){
		/**
		 * 1、校验学号工号，联系电话
		 * 2、根据工号/学号查找出教职工和学生
		 */
		//校验电话格式，判断查找到的学生或老师是否为空
		boolean mobileNum = Checkout.isMobileNum(telephone);
		if(mobileNum==false){
			HintoFront.alertMsg("联系方式手机号码输入错误", null, response);
			//return new ModelAndView("/jsp/student/stu_identify") ;
			return "/jsp/student/stu_identify";
		}
		Student stu = studentServiceImpl.findStu(name,number);
		//如果找不到此学生，提示姓名或者学号输入错误
		if(stu==null){
			HintoFront.alertMsg("姓名或者学号输入错误", null,response);
			//return  new ModelAndView("/jsp/student/stu_identify");
			return "/jsp/student/stu_identify";
		}
		//当用户信息正确，通过code获得openid,并将openid和student表 进行关联，同时保存用户的手机号
		String code = (String) request.getSession().getAttribute("code");
		String openid = WeixinUtil.getOpenid(code);
		studentServiceImpl.saveOpenId(openid,name,number,telephone);
		
		
		
		MicroStu mstu = new MicroStu();
		//填充微校必须的字段(学号，姓名，年级，学院/部门，专业，手机号和身份证不能同时为空)
		mstu.setState(state);
		mstu.setCard_number(stu.getStuCardNumber());//学号	
		mstu.setName(stu.getStuName());//姓名
		mstu.setCollege(stu.getStuCollege());//学院/部门
		mstu.setProfession(stu.getStuProfession());//专业
		mstu.setGrade(stu.getStuGrade());//年级
		mstu.setId_card(stu.getStuIDnum());
		mstu.setTelephone(stu.getStuPhone());
		mstu.setClassConflict("1班");
		mstu.setGender(stu.getStuSex());
		mstu.setCampus("");
		mstu.setDorm_number("");
		mstu.setIdentity_type(1);
		mstu.setIdentity_title("学生");
		mstu.setOrganization(stu.getStuCollege()+"/"+stu.getStuProfession()+"/"+stu.getStuClass());
		String jstu = JSONObject.toJSONString(mstu);
		String restu = jstu.replace("classConflict", "class");
		//对数据进行加密，post给微校服务器
		  /**
         * 加密用的Key 可以用26个字母和数字组成，最好不要用保留字符，
         * 虽然不会错，至于怎么裁决，个人看情况而定
         * key == AppSecret
         */
        //String cKey = "1234567890123456";
        String cKey = app_key;
        // 需要加密的字串
        String cSrc = restu;
        // app_secret 取前16位;
        String cIv = "C4960C80E48676D2";
        System.out.println(cSrc);
        // 加密
        long lStart = System.currentTimeMillis();
        String enString = null;
		try {
			enString = AES.Encrypt(cSrc, cKey, cIv);
		} catch (Exception e) {
			System.out.println("+++++加密错误+++++");
		}
        System.out.println("加密后的字串是：" + enString);
        long lUseTime = System.currentTimeMillis() - lStart;
        System.out.println("加密耗时：" + lUseTime + "毫秒");
        Rdata data =new Rdata();
        data.setApp_key(app_key);
        data.setRaw_data(enString);
        String para = JSONObject.toJSONString(data);
        String jsonSMS = PostWithJson.JsonSMS("http://weixiao.qq.com/apps/identity/receive-stu-info", para);
        request.getSession().setAttribute("state", state);
        System.out.println("这是返回的数据是吗+"+jsonSMS);
        
        // 解密
       /* lStart = System.currentTimeMillis();
        String DeString = AES.Decrypt(enString, cKey, cIv);
        System.out.println("解密后的字串是：" + DeString);
        lUseTime = System.currentTimeMillis() - lStart;
        System.out.println("解密耗时：" + lUseTime + "毫秒");*/
       /* String url ="https://weixiao.qq.com/apps/identity/login-callback?state="+state;
        ModelAndView downloadView = new ModelAndView(new RedirectView(url));
        return downloadView;*/
        return "/jsp/success/success";
		//return "redirect:/loadMicro?state="+state;
	}
	
}

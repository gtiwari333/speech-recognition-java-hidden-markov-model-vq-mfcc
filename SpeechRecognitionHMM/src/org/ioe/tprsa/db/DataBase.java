/*
  Please feel free to use/modify this class. 
  If you give me credit by keeping this information or
  by sending me an email before using it or by reporting bugs , i will be happy.
  Email : gtiwari333@gmail.com,
  Blog : http://ganeshtiwaridotcomdotnp.blogspot.com/ 
 */
package org.ioe.tprsa.db;

import java.util.List;

/**
 * @author Ganesh Tiwari
 */
public interface DataBase {

	public void setType(String type);

	public List<String> readRegistered();

	public Model readModel(String name) throws Exception;

	public void saveModel(Model m, String name) throws Exception;
}

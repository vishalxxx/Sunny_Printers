package service;

import model.User;
import repository.UserRepository;

public class LoginService {

	public UserRepository userrepo = new UserRepository();

	public boolean login(String username) {

		User user = null;
		try {
			user = userrepo.findByUsername(username);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (user != null) {

			System.out.println(
					user.getId() + "   " + user.getUsername() + "    " + user.getPassword() + "    " + user.getRole());
			return true;
		}

		else {
			System.out.println("****************NULLL USER **********************");

			return false;
		}

	}

}

package io.github.mschout.gitlab.toggltimer.home

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class HomeController {

  @GetMapping("/") fun index(): String = "index"
}

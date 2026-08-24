import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

@Component({
  selector: 'app-navbar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="navbar">
      <div class="navbar-inner">
        <!-- Brand -->
        <a routerLink="/dashboard" class="brand">
          <span class="brand-icon">▮</span>
          <span class="brand-name">AutoJudge</span>
        </a>

        <!-- Nav links -->
        <ul class="nav-links">
          <li>
            <a routerLink="/dashboard" routerLinkActive="active" class="nav-link">Dashboard</a>
          </li>
          <li>
            <a routerLink="/assignments" routerLinkActive="active" class="nav-link">Assignments</a>
          </li>
          <li>
            <a routerLink="/results" routerLinkActive="active" class="nav-link">Results</a>
          </li>
          <li>
            <a routerLink="/plagiarism" routerLinkActive="active" class="nav-link">Plagiarism</a>
          </li>
        </ul>
      </div>
    </nav>
  `,
  styles: [`
    .navbar {
      background: #fff;
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 100;
    }
    .navbar-inner {
      max-width: 1100px;
      margin: 0 auto;
      padding: 0 24px;
      height: 52px;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }
    .brand {
      display: flex;
      align-items: center;
      gap: 8px;
      text-decoration: none;
      color: var(--text-primary);
      font-weight: 600;
      font-size: 15px;
    }
    .brand-icon {
      background: #1a1a1a;
      color: #fff;
      font-size: 10px;
      width: 22px;
      height: 22px;
      display: flex;
      align-items: center;
      justify-content: center;
      border-radius: 4px;
    }
    .nav-links {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      gap: 4px;
    }
    .nav-link {
      text-decoration: none;
      color: var(--text-secondary);
      font-size: 13.5px;
      font-weight: 400;
      padding: 5px 10px;
      border-radius: 6px;
      transition: color 0.15s, background 0.15s;
    }
    .nav-link:hover {
      color: var(--text-primary);
      background: var(--bg);
    }
    .nav-link.active {
      color: var(--text-primary);
      font-weight: 600;
    }
  `]
})
export class NavbarComponent {}
